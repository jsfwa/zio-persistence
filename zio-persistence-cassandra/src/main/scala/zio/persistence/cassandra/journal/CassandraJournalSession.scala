package zio.persistence.cassandra.journal

import java.lang

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveRow
import com.datastax.oss.driver.api.core.cql.{BatchStatement, DefaultBatchType, PreparedStatement, Row}
import com.datastax.oss.driver.api.core.uuid.Uuids
import zio.cassandra.service.Session
import zio.persistence.journal._
import zio.{Task, ZIO, stream}

import scala.jdk.CollectionConverters._

class CassandraJournalSession(
  val underlying: Session,
  val config: CassandraJournalConfig,
  writeJournal: PreparedStatement,
  readJournal: PreparedStatement,
  readHighestPartitionSequenceNr: PreparedStatement,
  writeSnapshot: PreparedStatement,
  readSnapshot: PreparedStatement
) {
  import CassandraJournalSession._

  def saveSnapshot(snapshot: PersistentEvent): Task[Done] =
    for {
      bs <- underlying.bind(
             writeSnapshot,
             Seq(
               snapshot.persistenceId,
               snapshot.sequenceNr.asJava,
               Uuids.startOf(snapshot.timestamp),
               snapshot.serializerId,
               snapshot.writerId.toString,
               snapshot.manifest,
               snapshot.event
             ),
             config.snapshotProfileName
           )
      _ <- underlying.execute(bs)
    } yield ()

  def loadSnapshot(persistenceId: PersistenceId, criteria: SnapshotCriteria): Task[Option[Row]] =
    for {
      bs <- underlying.bind(readSnapshot,
                            Seq(persistenceId, criteria.minSequenceNr.asJava, criteria.maxSequenceNr.asJava))
      res <- underlying.selectOne(bs)
    } yield res

  def persistBatch(batch: AtomicBatch): Task[Done] = {
    val preparedEvents = batch.mapMPar { event =>
      underlying.bind(
        writeJournal,
        Seq(
          event.persistenceId,
          (event.sequenceNr / config.partitionSize).asJava,
          event.sequenceNr.asJava,
          Uuids.startOf(event.timestamp),
          event.serializerId,
          event.writerId.toString,
          event.manifest,
          event.event
        ),
        config.writeProfileName
      )
    }

    for {
      bs            <- preparedEvents
      preparedBatch = BatchStatement.newInstance(DefaultBatchType.LOGGED).addAll(bs.asJava)
      _             <- underlying.execute(preparedBatch)
    } yield ()
  }

  def read[E](persistenceId: PersistenceId,
              partitionNr: Long,
              fromSequenceNr: Long,
              toSequenceNr: Long): ZIO[Any, Throwable, stream.Stream[Throwable, ReactiveRow]] =
    for {
      bs <- underlying.bind(
             readJournal,
             Seq(
               persistenceId,
               partitionNr.asJava,
               fromSequenceNr.asJava,
               toSequenceNr.asJava
             )
           )

    } yield underlying.select(bs)

  def readHighestInPartition(persistenceId: PersistenceId, partitionNr: Long): Task[Option[Row]] =
    for {
      bs  <- underlying.bind(readHighestPartitionSequenceNr, Seq(persistenceId, partitionNr.asJava))
      res <- underlying.selectOne(bs)
    } yield res
}

object CassandraJournalSession {
  implicit class toJavaLong(val long: Long) extends AnyVal {
    def asJava: lang.Long = long.asInstanceOf[java.lang.Long]
  }
}
