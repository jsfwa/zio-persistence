package zio.persistence.journal

/*
  We assume that snapshots traversed from most current to oldest
 */
final case class SnapshotCriteria(
  minSequenceNr: Long = 0L,
  minTimestamp: Long = 0L,
  maxSequenceNr: Long = Long.MaxValue,
  maxTimestamp: Long = Long.MaxValue
)

object SnapshotCriteria {

  val default: SnapshotCriteria = SnapshotCriteria()

  val none: SnapshotCriteria = SnapshotCriteria(
    minSequenceNr = Long.MaxValue,
    minTimestamp = Long.MaxValue
  )

}
