package zio.persistence.benchmark

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import com.datastax.oss.driver.api.core.CqlSession
import com.dimafeng.testcontainers.CassandraContainer
import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._
import zio.cassandra.Session
import zio.persistence.benchmark.common.{SimplePersistenceEvent, ZBaseBench}
import zio.persistence.cassandra.journal.CassandraJournal
import zio.persistence.journal.AsyncJournal
import zio.stream.ZStream
import zio.{Chunk, RLayer, ZIO}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Measurement(iterations = 1, timeUnit = TimeUnit.SECONDS, time = 3)
@Warmup(iterations = 3, timeUnit = TimeUnit.SECONDS, time = 1)
@Threads(1)
class BasicRWBench extends ZBaseBench {

  val range = 1L.to(1000000L).map { SimplePersistenceEvent(_, "single#bench") }

  val multiRange = 1000001L.to(2000000L).map { i =>
    SimplePersistenceEvent(i, s"${i % 1000}#bench")
  }

  val chunk = Chunk.fromIterable(range)

  val multiChunk = Chunk.fromIterable(multiRange)

  var cassandraContainer: CassandraContainer = _

  def sessionLayer(cc: CassandraContainer) = {
    val address = new InetSocketAddress(cc.containerIpAddress, cc.mappedPort(9042))
    val builder = CqlSession
      .builder()
      .addContactPoint(address)
      .withLocalDatacenter("datacenter1")
    Session.Live.open(builder)
  }.toLayer

  val journalLayer: RLayer[Session, CassandraJournal] =
    CassandraJournal.create(ConfigFactory.load())

  @Setup
  def setup: Unit = {
    cassandraContainer = CassandraContainer("cassandra:3.11.6")
    cassandraContainer.start()
  }

  @TearDown
  def teardown: Unit =
    cassandraContainer.stop()

  @Benchmark
  def writeZIOSingleWriter1kk =
    runtime.unsafeRun((for {
      journal <- ZIO.service[AsyncJournal]
      _       <- Chunk.fromIterable(chunk.grouped(100).to(Iterable)).mapM(journal.persist)
    } yield ()).provideCustomLayer(sessionLayer(cassandraContainer) >>> journalLayer))

  @Benchmark
  def writeZIO10Writer100Batch =
    runtime.unsafeRun((for {
      journal <- ZIO.service[AsyncJournal]
      _       <- ZStream.fromIterable(multiChunk.grouped(100).to(Iterable)).mapMParUnordered(10)(journal.persist).runDrain
    } yield ()).provideCustomLayer(sessionLayer(cassandraContainer) >>> journalLayer))

  @Benchmark
  def writeZIO10Writer10Batch =
    runtime.unsafeRun((for {
      journal <- ZIO.service[AsyncJournal]
      _       <- ZStream.fromIterable(multiChunk.grouped(10).to(Iterable)).mapMParUnordered(10)(journal.persist).runDrain
    } yield ()).provideCustomLayer(sessionLayer(cassandraContainer) >>> journalLayer))

  //TODO: add akka persistence bench
}
