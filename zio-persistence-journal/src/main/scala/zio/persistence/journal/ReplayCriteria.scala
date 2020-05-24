package zio.persistence.journal

final case class ReplayCriteria(
  fromSequenceNr: Long = 0L,
  fromTimestamp: Long = 0L,
  toSequenceNr: Long = Long.MaxValue,
  toTimestamp: Long = Long.MaxValue
)

object ReplayCriteria {

  val default: ReplayCriteria = ReplayCriteria()

  val none: ReplayCriteria = ReplayCriteria(
    fromSequenceNr = Long.MaxValue,
    fromTimestamp = Long.MaxValue
  )

}
