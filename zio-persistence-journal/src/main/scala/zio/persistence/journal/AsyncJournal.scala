package zio.persistence.journal

import zio.Task
import zio.stream._

trait AsyncJournal {

  def persist(batch: AtomicBatch): Task[Done]

  def replay(replayRule: ReplayCriteria): Stream[Throwable, PersistentEvent]

  def saveSnapshot(snapshot: PersistentEvent): Task[Done]

  def loadSnapshot(criteria: SnapshotCriteria): Task[Option[PersistentEvent]]

  def shutdown() : Task[Done]

}
