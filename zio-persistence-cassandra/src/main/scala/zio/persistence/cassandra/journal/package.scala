package zio.persistence.cassandra

import java.nio.ByteBuffer
import java.util.UUID

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveRow
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.uuid.Uuids
import com.typesafe.config.Config
import zio.cassandra.service.Session
import zio.persistence.cassandra.common.CassandraConfig
import zio.persistence.cassandra.snapshot.CassandraSnapshotStatements
import zio.persistence.journal._
import zio.stream._
import zio.{ blocking => _, _ }

package object journal {

  type CassandraJournal = Has[AsyncJournal]

  case class PromisedWrite(batch: AtomicBatch, promise: Promise[Throwable, Done])
  case class ReplayState(prevPartitionNr: Long, partitionNr: Long, sequenceNr: Long)

  object CassandraJournal {
    import Live._

    def create(config: Config): RLayer[Has[Session], CassandraJournal] = live(config).toLayer

    def create(config: Config, session: Session): Layer[Throwable, CassandraJournal] = live(config, session).toLayer

  }

  class Live protected (queue: Queue[PromisedWrite], session: CassandraJournalSession) extends AsyncJournal {

    //Just workaround
    def startAsyncWriter(): Task[Done] =
      ZStream
        .fromQueueWithShutdown(queue)
        .mapMParUnordered(session.config.writeParallelism) { b =>
          session
            .persistBatch(b.batch)
            .tapBoth(e => b.promise.fail(e), _ => b.promise.succeed(()))
            .catchAll(_ => Task(())) //Should handle it via promise, stream must not fall
        }
        .runDrain

    /**
     * Since cassandra event log partitioning is handled on application level
     * next situations could be possible during replay:
     * - previous partition is drained and some records were deleted, next partitions is valid:
     *   have to check next partition
     * - full partition was deleted and need to skip it
     *    //TODO: this case is ignored right now and replay will stop here (need marking mechanism for deleted partitions)
     * - previous partition is drained and current partition is empty:
     *   complete stream
     *
     * @param replayRule
     * @return
     */
    override def replay(
      persistenceId: PersistenceId,
      replayRule: ReplayCriteria
    ): Stream[Throwable, PersistentEvent] = {

      //Previous cursor is necessary for stop point
      def internalRead(
        cursor: Ref[Long],
        previous: Ref[Long]
      ): ZIO[Any, Option[Throwable], stream.Stream[Throwable, ReactiveRow]] =
        for {
          current <- cursor.get
          inner <- previous.modify {
                    case x if x >= current => Left(Task.succeed(())) -> current
                    case x =>
                      val (partitionNr, from) =
                        if (x == -1L)
                          (current / session.config.partitionSize) -> current //If first batch, start from the specified offset
                        else {
                          //Read full partition, ready for the next
                          val p = current / session.config.partitionSize + 1
                          p -> (p * session.config.partitionSize)
                        }
                      val to = from + session.config.partitionSize
                      Right(session.read(persistenceId, partitionNr, from, to)) -> current

                  }.rightOrFail(Option.empty[Throwable])
          res <- inner.mapError(Some(_))
        } yield res

      val replayJob = for {
        previous <- Ref.make[Long](-1L)
        current  <- Ref.make[Long](replayRule.fromSequenceNr)
      } yield ZStream
        .repeatEffectOption(internalRead(current, previous))
        .flatMap(_.mapM { row =>
          val res: PersistentEvent = fromRow(row)
          current.set(res.sequenceNr).as(res)
        })

      ZStream.fromEffect(replayJob).flatten
    }

    /**
     * We want to have a control over write throughput and at the same
     * time want to provide guarantees of batch status
     * @param batch
     * @return
     */
    override def persist(batch: AtomicBatch): Task[Done] =
      for {
        p <- Promise.make[Throwable, Done]
        _ <- queue.offer(PromisedWrite(batch, p))
        r <- p.await
      } yield r

    override def saveSnapshot(snapshot: PersistentEvent): Task[Done] =
      session.saveSnapshot(snapshot)

    override def loadSnapshot(persistenceId: PersistenceId, criteria: SnapshotCriteria): Task[Option[PersistentEvent]] =
      session.loadSnapshot(persistenceId, criteria).map(_.map(fromRow))

    override def highestSequenceNr(persistenceId: PersistenceId): Task[Long] = {

      def maxPartitionNr(partitionNr: Long, highestSequenceNr: Long, skip: Long): Task[Long] =
        session.readHighestInPartition(persistenceId, partitionNr).flatMap {
          case Some(v)       => maxPartitionNr(partitionNr + 1L, v.getLong(0), skip = 0L)
          case _ if skip < 2 => maxPartitionNr(partitionNr + 1L, highestSequenceNr, skip + 1)
          case _             => Task.succeed(highestSequenceNr)
        }

      maxPartitionNr(0L, 0L, 0L)
    }

    private def fromRow(r: Row): PersistentEvent =
      new PersistentEvent {

        override def persistenceId: PersistenceId = r.getString("persistence_id")

        override def sequenceNr: Long = r.getLong("sequence_nr")

        override def timestamp: Long = Uuids.unixTimestamp(r.getUuid("timestamp"))

        override def serializerId: String = r.getString("serializer_id")

        override def writerId: UUID = UUID.fromString(r.getString("writer_uuid"))

        override def manifest: String = r.getString("event_manifest")

        override def event: ByteBuffer = r.getByteBuffer("event")
      }

  }

  object Live {

    def live(config: Config): RManaged[Has[Session], AsyncJournal] =
      for {
        session <- ZIO.service[Session].toManaged_
        journal <- live(config, session)
      } yield journal

    //Possible journal setup flow
    def live(config: Config, session: Session): TaskManaged[AsyncJournal] = {
      val journalConfig: Task[CassandraJournalConfig] =
        Task.fromTry(CassandraJournalConfig.fromConfig(config.getConfig("cassandra.persistence")))
      val journalSchemaConfig: Task[CassandraConfig] =
        Task.fromTry(CassandraConfig.fromConfig(config.getConfig("cassandra.persistence.journal")))
      val snapshotSchemaConfig: Task[CassandraConfig] =
        Task.fromTry(CassandraConfig.fromConfig(config.getConfig("cassandra.persistence.snapshot")))

      (for {
        jc                        <- journalConfig
        jsc                       <- journalSchemaConfig
        journalStatements         = new CassandraJournalStatements(jsc)
        _                         <- journalStatements.init(session)
        ssc                       <- snapshotSchemaConfig
        snapshotStatements        = new CassandraSnapshotStatements(ssc)
        _                         <- snapshotStatements.init(session)
        writeJournal              <- session.prepare(journalStatements.writeQuery)
        readJournal               <- session.prepare(journalStatements.selectQuery)
        readHighestPartitionSeqNr <- session.prepare(journalStatements.selectHighestSequenceNr)
        writeSnapshot             <- session.prepare(snapshotStatements.writeQuery)
        readSnapshot              <- session.prepare(snapshotStatements.selectQuery)
        journalSession = new CassandraJournalSession(
          session,
          jc,
          writeJournal,
          readJournal,
          readHighestPartitionSeqNr,
          writeSnapshot,
          readSnapshot
        )
        queue   <- Queue.bounded[PromisedWrite](jc.writeQueueSize)
        journal = new Live(queue, journalSession)
        _       <- journal.startAsyncWriter().fork
      } yield queue -> journal).toManaged(_._1.shutdown).map(_._2)
    }
  }

}
