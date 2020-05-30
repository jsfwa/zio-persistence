package zio.persistence.journal

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

trait PersistentEvent {

  /**
   * In case seconds instance of journal will attempt to write message
   * with same persistenceId and sequenceNr, we must ensure,
   * that messages from both writers will be persisted
   */
  def writerId: UUID

  def persistenceId: PersistenceId

  def sequenceNr: Long

  def serializerId: String

  def manifest: String

  def event: ByteBuffer

  def timestamp: Long

  override def toString: PersistenceId =
    s"""PersistenceEvent(
       |writerId = $writerId,
       |persistenceId = $persistenceId,
       |sequenceNr = $sequenceNr,
       |serializerId = $serializerId,
       |manifest = $manifest,
       |event = ${new String(event.array(), StandardCharsets.UTF_8)},
       |timestamp = ${Instant.ofEpochMilli(timestamp)}
       |)""".stripMargin

}
