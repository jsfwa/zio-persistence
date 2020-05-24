package zio.persistence.cassandra.journal

case class CassandraJournalConfig(
  partitionSize: Long,
  replayBatchSize: Int,
  replayParallelism: Int,
  writeParallelism: Int,
  writeQueueSize: Int,
  writeProfileName: String = "write-profile",
  replayProfileName: String = "replay-profile",
  snapshotProfileName: String = "snapshot-profile"
)
