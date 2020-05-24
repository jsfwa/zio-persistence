package zio.persistence.cassandra.common

sealed trait ReplicationStrategy {
  def toCQL: String
}

case class SimpleStrategy(replicationFactor: Int) extends ReplicationStrategy {
  override def toCQL: String =
    s"""{ 'class': 'SimpleStrategy', 'replication_factor' : $replicationFactor }"""
}

case class NetworkTopologyStrategy(dc: Seq[(String, Int)])
    extends ReplicationStrategy {
  def stringifyDC: String =
    dc.map { case (name, rf) => s"'$name': $rf" }.mkString(",")

  override def toCQL: String =
    s"""{ 'class': 'NetworkTopologyStrategy', $stringifyDC }"""
}
