lazy val journal =
  (project in file("zio-persistence-journal"))
    .settings(
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
      scalaVersion := "2.13.2",
      name := "zio-persistence-journal",
      libraryDependencies ++=
//        Dependencies.cassandraDependencies ++
          Dependencies.zioDependencies ++
          Dependencies.testCommon,
      scalacOptions ++= Seq(
        "-encoding",
        "utf-8",
        "-unchecked",
        "-explaintypes",
        "-Yrangepos",
        "-Ywarn-unused",
        "-Ymacro-annotations",
        "-deprecation",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Xlint:-serial",
        "-Xfatal-warnings",
        "-Werror",
        "-Wconf:any:error"
      ),
      sources in (Compile, doc) := Seq.empty,
      publishArtifact in GlobalScope in Test := false,
      publishArtifact in (Compile, packageDoc) := false,
      parallelExecution in Test := false
    )

lazy val persistenceCassandra = (project in file("zio-persistence-cassandra"))
  .dependsOn(journal)
  .settings(
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    scalaVersion := "2.13.2",
    name := "zio-persistence-cassandra",
    libraryDependencies ++=
              Dependencies.cassandraDependencies ++
      Dependencies.zioDependencies ++
        Dependencies.testCommon,
    scalacOptions ++= Seq(
      "-encoding",
      "utf-8",
      "-unchecked",
      "-explaintypes",
      "-Yrangepos",
      "-Ywarn-unused",
      "-Ymacro-annotations",
      "-deprecation",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-Xlint:-serial",
      "-Xfatal-warnings",
      "-Werror",
      "-Wconf:any:error"
    ),
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in GlobalScope in Test := false,
    publishArtifact in (Compile, packageDoc) := false,
    parallelExecution in Test := false
  )

lazy val root = (project in file("."))
  .aggregate(journal, persistenceCassandra)
  .settings(
    skip in publish := true
  )
