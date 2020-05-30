package zio.persistence.cassandra.snapshot

import zio.persistence.cassandra.common.{ CassandraConfig, CassandraStatements }

class CassandraSnapshotStatements(val config: CassandraConfig) extends CassandraStatements {

  val keyspaceQuery: String =
    s"""
       |CREATE KEYSPACE IF NOT EXISTS ${config.keyspace}
       | WITH REPLICATION = ${config.replicationStrategy.toCQL}
       """.stripMargin.trim

  val tableQuery: String = s"""
                              |CREATE TABLE IF NOT EXISTS $tableName (
                              |  persistence_id text,
                              |  sequence_nr bigint,
                              |  timestamp timeuuid,
                              |  serializer_id text,
                              |  writer_uuid text,
                              |  event_manifest text,
                              |  event blob,
                              |  PRIMARY KEY (persistence_id, sequence_nr))
                              |  WITH CLUSTERING ORDER BY (sequence_nr DESC) AND gc_grace_seconds =${config.gcGraceSeconds}
                              |  AND compaction = ${config.compactionStrategy.toCQL}
    """.stripMargin.trim

  val writeQuery: String =
    s"""
       |INSERT INTO $tableName (persistence_id, sequence_nr, timestamp, serializer_id, writer_uuid, event_manifest, event)
       |       VALUES (?, ?, ?, ?, ?, ?, ?)
       """.stripMargin.trim

  val selectQuery: String =
    s"""
       |SELECT * FROM ${tableName} WHERE
       |        persistence_id = ? AND
       |        sequence_nr >= ? AND
       |        sequence_nr <= ? 
       |        ORDER BY sequence_nr
       |        DESC
       |        LIMIT 1
       """.stripMargin.trim
}
