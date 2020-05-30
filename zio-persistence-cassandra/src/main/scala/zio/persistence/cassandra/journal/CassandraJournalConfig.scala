package zio.persistence.cassandra.journal

import com.typesafe.config.Config

import scala.util.Try

case class CassandraJournalConfig(
  partitionSize: Long,
  writeParallelism: Int,
  writeQueueSize: Int,
  writeProfileName: String,
  replayProfileName: String,
  snapshotProfileName: String
)

object CassandraJournalConfig {

  def fromConfig(config: Config): Try[CassandraJournalConfig] = Try {
    CassandraJournalConfig(
      partitionSize = config.getLong("partition-size"),
      writeParallelism = config.getInt("write-parallelism"),
      writeQueueSize = config.getInt("write-queue-size"),
      writeProfileName = config.getString("write-profile-name"),
      replayProfileName = config.getString("replay-profile-name"),
      snapshotProfileName = config.getString("snapshot-profile-name")
    )
  }

}
