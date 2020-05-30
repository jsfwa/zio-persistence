package zio.persistence.cassandra.common

import com.typesafe.config.Config

import scala.jdk.CollectionConverters._
import scala.util.Try

case class CassandraConfig(
  migration: Boolean,
  keyspace: String,
  replicationStrategy: ReplicationStrategy,
  gcGraceSeconds: Int,
  tableName: String,
  compactionStrategy: CompactionStrategy,
)

object CassandraConfig {

  def fromConfig(config: Config): Try[CassandraConfig] = Try {
    CassandraConfig(
      migration = config.getBoolean("migration"),
      keyspace = config.getString("keyspace"),
      replicationStrategy = getReplicationStrategy(config.getConfig("replication-strategy")),
      gcGraceSeconds = config.getInt("gc-grace-seconds"),
      tableName = config.getString("table"),
      compactionStrategy = getCompactionStrategy(config.getConfig("compaction-strategy"))
    )
  }

  def getReplicationStrategy(config: Config): ReplicationStrategy =
    if (config.hasPath("replication-factor")) {
      SimpleStrategy(config.getInt("replication-factor"))
    } else {
      val dc = config.getObject(".").unwrapped().asScala.view.mapValues(_.asInstanceOf[Int]).toList
      NetworkTopologyStrategy(dc)
    }

  def getCompactionStrategy(config: Config): CompactionStrategy =
    if (config.getString("class") == "SizeTieredCompactionStrategy")
      CompactionStrategy.stcsFromConfig(config)
    else
      CompactionStrategy.lcsFromConfig(config)
}
