package zio.persistence.benchmark.common

import zio.internal.Platform
import zio.{BootstrapRuntime, Runtime}

trait ZBaseBench extends BootstrapRuntime {

  override val platform: Platform = Platform.benchmark

  val ConcurrentBucketsImpl = "ConcurrentBuckets"
  val AtomicRefMapImpl      = "AtomicRefMap"

  val rnd = new scala.util.Random()

  val runtime = Runtime(environment, platform)

}
