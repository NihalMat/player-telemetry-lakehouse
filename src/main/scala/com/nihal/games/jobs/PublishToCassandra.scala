package com.nihal.games.jobs

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._

object PublishToCassandra {
  private def writeTable(data: DataFrame, table: String): Unit =
    data.write
      .format("org.apache.spark.sql.cassandra")
      .mode("append")
      .options(Map("keyspace" -> "game_analytics", "table" -> table))
      .save()

  def main(args: Array[String]): Unit = {
    val catalog = sys.env.getOrElse("ICEBERG_CATALOG", "game")
    val warehouse = sys.env.getOrElse("ICEBERG_WAREHOUSE", "hdfs://localhost:9000/warehouse")

    val spark = SparkSession.builder()
      .appName("publish-player-analytics-to-cassandra")
      .config(s"spark.sql.catalog.$catalog", "org.apache.iceberg.spark.SparkCatalog")
      .config(s"spark.sql.catalog.$catalog.type", "hadoop")
      .config(s"spark.sql.catalog.$catalog.warehouse", warehouse)
      .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .config("spark.cassandra.connection.host", sys.env.getOrElse("CASSANDRA_HOST", "localhost"))
      .config("spark.sql.session.timeZone", "UTC")
      .getOrCreate()

    writeTable(
      spark.table(s"$catalog.analytics.daily_engagement")
        .withColumnRenamed("event_date", "activity_date")
        .withColumn("activity_month", date_format(col("activity_date"), "yyyy-MM"))
        .select(
          "game_id",
          "activity_month",
          "activity_date",
          "platform",
          "build",
          "country_code",
          "dau",
          "sessions",
          "avg_session_seconds",
          "p95_session_seconds",
          "crash_free_session_rate",
          "revenue_usd"
        ),
      "daily_engagement_by_game_day"
    )

    writeTable(
      spark.table(s"$catalog.analytics.cohort_retention")
        .withColumn("cohort_month", date_format(col("cohort_date"), "yyyy-MM"))
        .select(
          "game_id",
          "cohort_month",
          "cohort_date",
          "platform",
          "build",
          "country_code",
          "cohort_size",
          "retained_d1_players",
          "retained_d7_players",
          "retained_d30_players",
          "d1_retention",
          "d7_retention",
          "d30_retention"
        ),
      "cohort_retention_by_game_day"
    )

    writeTable(
      spark.table(s"$catalog.analytics.level_progression")
        .select(
          "game_id",
          "event_date",
          "platform",
          "build",
          "country_code",
          "level",
          "players_started",
          "players_failed",
          "players_completed",
          "completion_rate"
        ),
      "level_progression_by_game_day"
    )

    writeTable(
      spark.table(s"$catalog.analytics.economy_flows")
        .select(
          "game_id",
          "event_date",
          "platform",
          "build",
          "country_code",
          "virtual_currency",
          "item_type",
          "supporting_players",
          "currency_source",
          "currency_sink",
          "net_currency_flow"
        ),
      "economy_flows_by_game_day"
    )

    spark.stop()
  }
}
