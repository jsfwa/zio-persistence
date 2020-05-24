package zio.persistence.journal

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

  def event: Array[Byte]

  def timestamp: Long

}

