package zio.persistence.cassandra.common

import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

import zio.persistence.journal.{ PersistenceId, PersistentEvent }

object SimplePersistenceEvent {

  def apply(writer: UUID, seqNr: Long, pid: PersistenceId): PersistentEvent = new PersistentEvent {

    override def writerId: UUID = writer

    override def persistenceId: PersistenceId = pid

    override def sequenceNr: Long = seqNr

    override def serializerId: String = "test"

    override def manifest: String = "test"

    override def event: ByteBuffer = ByteBuffer.wrap(Array.empty[Byte])

    override def timestamp: Long = Instant.now().toEpochMilli
  }

}
