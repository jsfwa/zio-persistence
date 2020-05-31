import sbt._

object Dependencies {

  val zioVersion = "1.0.0-RC20"

  val zioCassandraVersion = "0.0.6-SNAPSHOT"

  val javaStreamsInterop = "1.0.3.5-RC8"

  val cassandraDependencies = Seq(
    "dev.zio.local" %% "zio-cassandra" % zioCassandraVersion
  )

  val zioDependencies = Seq(
    "dev.zio" %% "zio"                         % zioVersion,
    "dev.zio" %% "zio-streams"                 % zioVersion,
    "dev.zio" %% "zio-interop-reactivestreams" % javaStreamsInterop
  )

  val cassandraContainers = Seq(
    "com.dimafeng"       %% "testcontainers-scala-core"      % "0.37.0",
    "com.dimafeng"       %% "testcontainers-scala-cassandra" % "0.37.0",
  )

  val testCommon = Seq(
    "org.wvlet.airframe" %% "airframe-log"                   % "20.5.1",
    "org.slf4j"          % "slf4j-jdk14"                     % "1.7.21",
    "dev.zio"            %% "zio-test"                       % zioVersion,
    "dev.zio"            %% "zio-test-sbt"                   % zioVersion
  ).map(_ % Test)

}
