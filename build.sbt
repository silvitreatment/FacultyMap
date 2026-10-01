ThisBuild / scalaVersion := "3.3.8"
ThisBuild / organization := "dev.facultymap"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    name := "faculty-map",
    Compile / mainClass := Some("facultymap.Main"),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.1.26",
      "dev.zio" %% "zio-json" % "0.9.0",
      "dev.zio" %% "zio-interop-cats" % "23.1.0.13",
      "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % "1.13.22",
      "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.13.22",
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.13.22",
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC12",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC12",
      "org.postgresql" % "postgresql" % "42.7.8",
      "io.scalaland" %% "chimney" % "1.8.2",
      "dev.zio" %% "zio-test" % "2.1.26" % Test,
      "dev.zio" %% "zio-test-sbt" % "2.1.26" % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Werror")
  )
