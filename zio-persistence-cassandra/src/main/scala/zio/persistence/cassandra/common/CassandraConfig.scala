package zio.persistence.cassandra.common

case class CassandraConfig(
                            migration: Boolean,
                            keyspace: String,
                            replicationStrategy: ReplicationStrategy,
                            gcGraceSeconds: Int,
                            tableName: String,
                            compactionStrategy: CompactionStrategy,
                          )

object CassandraConfig{

}