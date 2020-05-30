package zio.persistence.cassandra

import java.util.UUID

import com.typesafe.config.{ Config, ConfigFactory }
import wvlet.log.LogSupport
import zio.cassandra.Session
import zio.persistence.cassandra.common.{ SimplePersistenceEvent, TestSession }
import zio.persistence.cassandra.journal.CassandraJournal
import zio.persistence.journal.{ AsyncJournal, PersistentEvent, ReplayCriteria, SnapshotCriteria }
import zio.test.Assertion._
import zio.test.{ DefaultRunnableSpec, _ }
import zio.{ blocking => _, test => _, _ }

object BasicSpec extends DefaultRunnableSpec with LogSupport {

  val defaultConfig: Config = ConfigFactory.load()

  val journalLayer: RLayer[Session, CassandraJournal] =
    CassandraJournal.create(defaultConfig.getConfig("basic-spec"))

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("Basic flow")(
    testM("Write events -> snapshot -> events -> replay") {
      (for {
        journal  <- ZIO.service[AsyncJournal]
        pid      = "TestPersistenceId"
        writer   = UUID.randomUUID()
        _        <- journal.persist(Chunk.single(SimplePersistenceEvent(writer, 1L, pid)))
        _        <- journal.saveSnapshot(SimplePersistenceEvent(writer, 1L, pid)) //Must be ignored
        _        <- journal.persist(Chunk.single(SimplePersistenceEvent(writer, 2L, pid)))
        _        <- journal.saveSnapshot(SimplePersistenceEvent(writer, 3L, pid))
        _        <- journal.persist(Chunk.single(SimplePersistenceEvent(writer, 4L, pid)))
        _        <- journal.persist(Chunk.single(SimplePersistenceEvent(writer, 5L, pid)))
        snapshot <- journal.loadSnapshot(pid, SnapshotCriteria.default)
        replayed <- journal
                     .replay(
                       pid,
                       ReplayCriteria.default.copy(fromSequenceNr = snapshot.map(_.sequenceNr + 1L).getOrElse(0L))
                     )
                     .fold(List.empty[PersistentEvent]) { case (a, b) => b +: a }
                     .map(_.reverse)
        highest <- journal.highestSequenceNr(pid)
      } yield {
        info(s"Snapshot loaded ${snapshot}")
        info(s"Replayed events: ${replayed}")
        info(s"Highest sequence nr: $highest")
        assert(snapshot.map(_.sequenceNr))(equalTo(Some(3L))) &&
        assert(replayed.map(_.sequenceNr))(equalTo(List(4L, 5L))) &&
        assert(highest)(equalTo(5L))
      }).provideCustomLayer(TestSession.sessionLayer >>> journalLayer)
    }
  )
}
