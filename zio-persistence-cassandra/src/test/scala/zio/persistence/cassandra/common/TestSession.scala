package zio.persistence.cassandra.common

import java.net.InetSocketAddress

import com.datastax.oss.driver.api.core.CqlSession
import com.dimafeng.testcontainers.CassandraContainer
import zio.cassandra.Session
import zio.persistence.cassandra.container.ZTestContainer
import zio.test.TestFailure

object TestSession {

  val layaerCassandra = ZTestContainer.cassandra

  val layerSession = (for {
    cassandra <- ZTestContainer[CassandraContainer].toManaged_
    session <- {
      val address = new InetSocketAddress(cassandra.containerIpAddress, cassandra.mappedPort(9042))
      val builder = CqlSession
        .builder()
        .addContactPoint(address)
        .withLocalDatacenter("datacenter1")
      Session.Live.open(builder)
    }
  } yield session).toLayer.mapError(TestFailure.die)

  val sessionLayer = layaerCassandra >+> layerSession
}
