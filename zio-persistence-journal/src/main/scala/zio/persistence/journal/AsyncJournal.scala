package zio.persistence.journal

import zio.Task
import zio.stream._

trait AsyncJournal {

  def highestSequenceNr(persistenceId: PersistenceId): Task[Long]

  def persist(batch: AtomicBatch): Task[Done]

  def replay(persistenceId: PersistenceId, replayRule: ReplayCriteria): Stream[Throwable, PersistentEvent]

  def saveSnapshot(snapshot: PersistentEvent): Task[Done]

  def loadSnapshot(persistenceId: PersistenceId, criteria: SnapshotCriteria): Task[Option[PersistentEvent]]

}
