package zio.persistence.cassandra

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveRow
import com.typesafe.config.Config
import zio.blocking._
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

    def create(config: Config): ZLayer[Blocking with Has[Session], Throwable, CassandraJournal] = makeLayer(live(config))

    def create(config: Config, session: Session): ZLayer[Blocking, Throwable, CassandraJournal] = makeLayer(live(config, session))

  }

  class Live protected (queue: Queue[PromisedWrite], session: CassandraJournalSession) extends AsyncJournal {

    //Just workaround
    def startAsyncWriter(): ZIO[Blocking, Throwable, Done] =
      ZStream
        .fromQueueWithShutdown(queue)
        .mapMParUnordered(session.config.writeParallelism) { b =>
          blocking(session.persistBatch(b.batch))
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
    override def replay(replayRule: ReplayCriteria): Stream[Throwable, PersistentEvent] = {

      //Previous cursor is necessary for stop point
      def internalRead(cursor: Ref[Long],
                       previous: Ref[Long]): ZIO[Any, Option[Throwable], stream.Stream[Throwable, ReactiveRow]] =
        for {
          current <- cursor.get
          inner <- previous.modify {
                    case x if x >= current => Left(Task.succeed(())) -> current
                    case x =>
                      val from =
                        if (x == -1L) current //If first batch, start from the specified offset
                        else
                          (current / session.config.partitionSize + 1) * session.config.partitionSize //Read full partition, ready for the next
                      val to = from + session.config.partitionSize
                      Right(session.read(from, to)) -> current

                  }.rightOrFail(Option.empty[Throwable])
          res <- inner.mapError(Some(_))
        } yield res

      val replayJob = for {
        previous <- Ref.make[Long](-1L)
        current  <- Ref.make[Long](replayRule.fromSequenceNr)
      } yield
        ZStream
          .repeatEffectOption(internalRead(current, previous))
          .flatMap(_.mapMPar(session.config.replayParallelism) { _ =>
            val res: PersistentEvent = ??? //TODO row => persistent event
            current.set(res.sequenceNr + 1L).as(res)
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
        r <- p.await //Do we really need await here or callback will be less expensive?
      } yield r

    override def saveSnapshot(snapshot: PersistentEvent): Task[Done] =
      session.saveSnapshot(snapshot)

    override def loadSnapshot(criteria: SnapshotCriteria): Task[Option[PersistentEvent]] =
      session.loadSnapshot(criteria).map{_.map(_ => {val res: PersistentEvent = ???; res})}//TODO: add row => persistent event

    override def shutdown(): Task[Done] = queue.shutdown
  }

  object Live {

    def live(config: Config): ZIO[Blocking with Has[Session], Throwable, AsyncJournal] =
      for {
        session <- ZIO.service[Session]
        journal <- live(config, session)
      } yield journal

    //Possible journal setup flow
    def live(config: Config, session: Session): RIO[Blocking, AsyncJournal] = {
      //TODO: config initialization
      println(config)
      val journalConfig: Task[CassandraJournalConfig] = ???
      val journalSchemaConfig: Task[CassandraConfig]  = ???
      val snapshotSchemaConfig: Task[CassandraConfig] = ???

      for {
        jc                 <- journalConfig
        jsc                <- journalSchemaConfig
        journalStatements  = new CassandraJournalStatements(jsc)
        _                  <- journalStatements.init(session)
        ssc                <- snapshotSchemaConfig
        snapshotStatements = new CassandraSnapshotStatements(ssc)
        _                  <- snapshotStatements.init(session)
        writeJournal       <- session.prepare(journalStatements.writeQuery)
        readJournal        <- session.prepare(journalStatements.selectQuery)
        writeSnapshot      <- session.prepare(snapshotStatements.writeQuery)
        readSnapshot       <- session.prepare(snapshotStatements.selectQuery)
        journalSession = new CassandraJournalSession(session,
                                                     jc,
                                                     writeJournal,
                                                     readJournal,
                                                     writeSnapshot,
                                                     readSnapshot)
        queue   <- Queue.bounded[PromisedWrite](jc.writeQueueSize)
        journal = new Live(queue, journalSession)
        _       <- journal.startAsyncWriter().fork
      } yield journal
    }

    def make[R](journal: RIO[R, AsyncJournal]): ZManaged[R, Throwable, AsyncJournal] =
      journal.toManaged(_.shutdown().orDie)

    def makeLayer[R](journal: RIO[R, AsyncJournal]): ZLayer[R, Throwable, CassandraJournal] =
      make(journal).toLayer
  }

}
