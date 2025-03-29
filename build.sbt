ThisBuild / version := "0.0.1-SNAPSHOT"

ThisBuild / scalaVersion := "3.6.4"
ThisBuild / organization := "com.github.zio-schwab-api"

val zioVersion = "2.1.16"

val zioConfigVersion = "4.0.4"
lazy val zioSchwabApi = (project in file("."))
  .settings(
    name := "zio-schwab-api",

    libraryDependencies ++= List(
      "org.specs2" %% "specs2-core" % "5.5.8",
    ).map(_ % Test),

    libraryDependencies ++= List(
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-http" % "3.2.0",
      "dev.zio" %% "zio-json" % "0.7.39",
      "dev.zio" %% "zio-logging-slf4j2" % "2.5.0",
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    ),

    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "5.5.8",
      "dev.zio" %% "zio-test" % zioVersion,
      "dev.zio" %% "zio-test-sbt" % zioVersion,
      "dev.zio" %% "zio-test-magnolia" % zioVersion,
    ).map(_ % Test),
  )


testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")