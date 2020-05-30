package zio.persistence

import zio.{ blocking => _, _ }

package object journal {

  type Done = Unit

  type PersistenceId = String

  type AtomicBatch = Chunk[PersistentEvent]

}
