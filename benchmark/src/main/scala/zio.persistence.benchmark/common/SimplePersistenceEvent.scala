package zio.persistence.benchmark.common

import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

import zio.persistence.journal.{PersistenceId, PersistentEvent}

import scala.util.Random

object SimplePersistenceEvent {

  val rnd = Random

  val payload = ByteBuffer.wrap(rnd.nextBytes(200))

  def apply(seqNr: Long, pid: PersistenceId): PersistentEvent = new PersistentEvent {

    override def writerId: UUID = UUID.randomUUID()

    override def persistenceId: PersistenceId = pid

    override def sequenceNr: Long = seqNr

    override def serializerId: String = "test"

    override def manifest: String = "test"

    override def event: ByteBuffer = payload

    override def timestamp: Long = Instant.now().toEpochMilli
  }

}
