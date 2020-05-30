package zio.persistence.cassandra.common

import com.typesafe.config.Config

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
  SSTableSizeInMB: Int = 50,
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

object CompactionStrategy {

  def commonCompactionSettings(config: Config): CompactionStrategy = new CompactionStrategy {
    override def enabled: Boolean = config.getBoolean("enabled")

    override def tombstoneCompactionInterval: Long = config.getLong("tombstone-compaction-interval")

    override def tombstoneThreshold: Double = config.getDouble("tombstone-threshold")

    override def uncheckedTombstoneCompaction: Boolean = config.getBoolean("unchecked-tombstone-compaction")
  }

  def stcsFromConfig(config: Config): SizedTieredCompactionStrategy = {
    val common = commonCompactionSettings(config)
    SizedTieredCompactionStrategy(
      bucketHigh = config.getDouble("bucket-high"),
      bucketLow = config.getDouble("bucket-low"),
      maxThreshold = config.getInt("max-threshold"),
      minThreshold = config.getInt("min-threshold"),
      minSSTableSize = config.getInt("min-sstable-size"),
      enabled = common.enabled,
      tombstoneCompactionInterval = common.tombstoneCompactionInterval,
      tombstoneThreshold = common.tombstoneThreshold,
      uncheckedTombstoneCompaction = common.uncheckedTombstoneCompaction
    )
  }

  def lcsFromConfig(config: Config): LeveledCompactionStrategy = {
    val common = commonCompactionSettings(config)
    LeveledCompactionStrategy(
      SSTableSizeInMB = config.getInt("sstable-size-in-mb"),
      enabled = common.enabled,
      tombstoneCompactionInterval = common.tombstoneCompactionInterval,
      tombstoneThreshold = common.tombstoneThreshold,
      uncheckedTombstoneCompaction = common.uncheckedTombstoneCompaction
    )
  }
}
