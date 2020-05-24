package zio.persistence.cassandra.common

sealed trait CompactionStrategy {

  def enabled: Boolean
  def tombstoneCompactionInterval: Long
  def tombstoneThreshold: Double
  def uncheckedTombstoneCompaction: Boolean

  def toCQL: String =
    s"""'enabled' : $enabled,
         |'tombstone_compaction_interval' : $tombstoneCompactionInterval,
         |'tombstone_threshold' : $tombstoneThreshold,
         |'unchecked_tombstone_compaction' : $uncheckedTombstoneCompaction
         """.stripMargin.trim
}

case class SizedTieredCompactionStrategy(
    bucketHigh: Double = 1.5,
    bucketLow: Double = 0.5,
    maxThreshold: Int = 32,
    minThreshold: Int = 4,
    minSSTableSize: Int = 50,
    enabled: Boolean = true,
    tombstoneCompactionInterval: Long = 86400,
    tombstoneThreshold: Double = 0.2,
    uncheckedTombstoneCompaction: Boolean = false
) extends CompactionStrategy {
  override def toCQL: String = s"""{
                                    |'class' : 'SizeTieredCompactionStrategy',
                                    |${super.toCQL},
                                    |'bucket_high' : $bucketHigh,
                                    |'bucket_low' : $bucketLow,
                                    |'max_threshold' : $maxThreshold,
                                    |'min_threshold' : $minThreshold,
                                    |'min_sstable_size' : $minSSTableSize
                                    |}
     """.stripMargin.trim
}

case class LeveledCompactionStrategy(
    SSTableSizeInMB: Int,
    enabled: Boolean = true,
    tombstoneCompactionInterval: Long = 86400,
    tombstoneThreshold: Double = 0.2,
    uncheckedTombstoneCompaction: Boolean = false
) extends CompactionStrategy {
  override def toCQL: String = s"""{
                                    |'class' : 'LeveledCompactionStrategy',
                                    |${super.toCQL},
                                    |'sstable_size_in_mb' : $SSTableSizeInMB
                                    |}
     """.stripMargin.trim
}
