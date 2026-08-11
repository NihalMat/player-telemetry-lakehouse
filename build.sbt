ThisBuild / organization := "com.nihal"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.12.19"

lazy val root = (project in file("."))
  .settings(
    name := "player-telemetry-lakehouse",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql" % "3.5.2" % "provided",
      "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.5.2",
      "org.apache.iceberg" %% "iceberg-spark-runtime-3.5" % "1.6.1",
      "com.datastax.spark" %% "spark-cassandra-connector" % "3.5.1",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test
    ),
    Test / fork := true,
    assembly / assemblyJarName := "player-telemetry-lakehouse-assembly-0.1.0.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )
