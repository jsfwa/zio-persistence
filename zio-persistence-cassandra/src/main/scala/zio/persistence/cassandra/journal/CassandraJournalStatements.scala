package zio.persistence.cassandra.journal

import zio.persistence.cassandra.common.{CassandraConfig, CassandraStatements}

class CassandraJournalStatements(val config: CassandraConfig) extends CassandraStatements {

  val keyspaceQuery: String =
    s"""
       |CREATE KEYSPACE IF NOT EXISTS ${config.keyspace}
       | WITH REPLICATION = ${config.replicationStrategy.toCQL}
       """.stripMargin.trim

  val tableQuery: String = s"""
                              |CREATE TABLE IF NOT EXISTS $tableName (
                              |  persistence_id text,
                              |  partition_nr bigint,
                              |  sequence_nr bigint,
                              |  timestamp timeuuid,
                              |  serializer_id text,
                              |  writer_uuid text,
                              |  event_manifest text,
                              |  event blob,
                              |  PRIMARY KEY ((persistence_id, partition_nr), sequence_nr, timestamp))
                              |  WITH gc_grace_seconds =${config.gcGraceSeconds}
                              |  AND compaction = ${config.compactionStrategy.toCQL}
    """.stripMargin.trim

  val writeQuery: String =
    s"""
       |INSERT INTO $tableName (persistence_id, partition_nr, sequence_nr, timestamp, serializer_id, writer_uuid, event_manifest, event)
       |       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       """.stripMargin.trim

  val selectQuery: String =
    s"""
       |SELECT * FROM ${tableName} WHERE
       |       persistence_id = ? AND
       |       partition_nr = ? AND
       |       sequence_nr >= ? AND
       |       sequence_nr <= ?
       """.stripMargin.trim

  val selectHighestSequenceNr: String =
    s"""
       |SELECT sequence_nr FROM ${tableName} WHERE
       |       persistence_id = ? AND
       |       partition_nr = ?
       |       ORDER BY sequence_nr
       |       DESC LIMIT 1
    """.stripMargin.trim

}
