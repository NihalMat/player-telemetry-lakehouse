package com.nihal.games.jobs

import com.nihal.games.validation.EventRules
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._

object ValidateGameEvents {
  private def argument(args: Array[String], name: String, default: String): String = {
    val prefix = s"--$name="
    args.collectFirst {
      case value if value.startsWith(prefix) => value.substring(prefix.length)
    }.getOrElse(default)
  }

  def main(args: Array[String]): Unit = {
    val runDate = argument(args, "run-date", java.time.LocalDate.now().minusDays(1).toString)
    val catalog = sys.env.getOrElse("ICEBERG_CATALOG", "game")
    val warehouse = sys.env.getOrElse("ICEBERG_WAREHOUSE", "hdfs://localhost:9000/warehouse")
    val rawTable = sys.env.getOrElse("RAW_EVENT_TABLE", s"$catalog.raw.game_events")

    val spark = SparkSession.builder()
      .appName("validate-game-events")
      .config(s"spark.sql.catalog.$catalog", "org.apache.iceberg.spark.SparkCatalog")
      .config(s"spark.sql.catalog.$catalog.type", "hadoop")
      .config(s"spark.sql.catalog.$catalog.warehouse", warehouse)
      .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .config("spark.sql.session.timeZone", "UTC")
      .getOrCreate()

    val events = spark.table(rawTable)
      .filter(col("event_date") === to_date(lit(runDate)))
      .cache()

    val rowCount = events.count()
    val missingRequired = events.filter(
      col("event_uuid").isNull ||
        col("game_id").isNull ||
        col("player_key").isNull ||
        col("session_id").isNull ||
        col("event_time").isNull ||
        col("category").isNull
    ).count()
    val duplicateEventIds = events.groupBy("event_uuid").count().filter(col("count") > 1).count()
    val unsupportedCategories = events
      .filter(!col("category").isin(EventRules.SupportedCategories.toSeq: _*))
      .count()

    val failures = Seq(
      if (rowCount == 0) Some(s"No events found for $runDate") else None,
      if (missingRequired > 0) Some(s"$missingRequired rows have missing required fields") else None,
      if (duplicateEventIds > 0) Some(s"$duplicateEventIds duplicate event identifiers were found") else None,
      if (unsupportedCategories > 0) Some(s"$unsupportedCategories unsupported categories were found") else None
    ).flatten

    events.unpersist()
    spark.stop()

    if (failures.nonEmpty) {
      failures.foreach(message => Console.err.println(s"QUALITY_CHECK_FAILED: $message"))
      sys.exit(1)
    }

    println(s"QUALITY_CHECK_PASSED: $rowCount events validated for $runDate")
  }
}
