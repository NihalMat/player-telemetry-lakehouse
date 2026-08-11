package com.nihal.games.jobs

import org.apache.spark.sql.SparkSession

object IcebergMaintenance {
  def main(args: Array[String]): Unit = {
    val catalog = sys.env.getOrElse("ICEBERG_CATALOG", "game")
    val warehouse = sys.env.getOrElse("ICEBERG_WAREHOUSE", "hdfs://localhost:9000/warehouse")
    val tables = Seq(
      s"$catalog.raw.game_events",
      s"$catalog.analytics.player_sessions",
      s"$catalog.analytics.daily_engagement",
      s"$catalog.analytics.cohort_retention",
      s"$catalog.analytics.level_progression",
      s"$catalog.analytics.economy_flows"
    )

    val spark = SparkSession.builder()
      .appName("iceberg-game-analytics-maintenance")
      .config(s"spark.sql.catalog.$catalog", "org.apache.iceberg.spark.SparkCatalog")
      .config(s"spark.sql.catalog.$catalog.type", "hadoop")
      .config(s"spark.sql.catalog.$catalog.warehouse", warehouse)
      .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .getOrCreate()

    tables.foreach { table =>
      spark.sql(s"CALL $catalog.system.rewrite_data_files(table => '$table')")
      spark.sql(
        s"CALL $catalog.system.expire_snapshots(table => '$table', older_than => TIMESTAMP '${java.time.LocalDate.now().minusDays(7)} 00:00:00')"
      )
    }

    spark.stop()
  }
}
