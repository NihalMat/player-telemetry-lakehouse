package com.nihal.games.jobs

import com.nihal.games.privacy.PrivacyTransforms
import org.apache.spark.sql.{Column, DataFrame, SparkSession}
import org.apache.spark.sql.functions._

object BuildPlayerAnalytics {
  private val MinimumPrivacyGroup = 20
  private val DailyDimensions = Seq("game_id", "event_date", "platform", "build", "country_code")

  def buildSessions(events: DataFrame): DataFrame =
    events
      .groupBy(
        col("game_id"),
        col("player_key"),
        col("session_id"),
        col("event_date"),
        col("platform"),
        col("build"),
        col("country_code")
      )
      .agg(
        min("event_time").as("session_started_at"),
        max("event_time").as("session_ended_at"),
        count(lit(1)).as("event_count"),
        max(when(col("category") === "error", lit(1)).otherwise(lit(0))).as("has_error")
      )
      .withColumn(
        "session_seconds",
        greatest(lit(0L), unix_timestamp(col("session_ended_at")) - unix_timestamp(col("session_started_at")))
      )

  def buildDailyEngagement(sessions: DataFrame, events: DataFrame): DataFrame = {
    val revenue = events
      .filter(col("category") === "business")
      .groupBy(DailyDimensions.map(col): _*)
      .agg((sum(coalesce(col("amount"), lit(0L))) / lit(100.0)).as("revenue_usd"))

    val engagement = sessions
      .groupBy(DailyDimensions.map(col): _*)
      .agg(
        countDistinct("player_key").as("dau"),
        countDistinct("session_id").as("sessions"),
        avg("session_seconds").as("avg_session_seconds"),
        expr("percentile_approx(session_seconds, 0.95)").as("p95_session_seconds"),
        (lit(1.0) - avg(col("has_error").cast("double"))).as("crash_free_session_rate")
      )
      .join(revenue, DailyDimensions, "left")
      .na.fill(0.0, Seq("revenue_usd"))

    PrivacyTransforms.suppressSmallGroups(engagement, col("dau"), MinimumPrivacyGroup)
  }

  def buildRetention(events: DataFrame): DataFrame = {
    val activity = events
      .select(
        "game_id",
        "player_key",
        "signup_date",
        "event_date",
        "platform",
        "build",
        "country_code"
      )
      .filter(col("signup_date").isNotNull)
      .dropDuplicates("game_id", "player_key", "event_date")
      .withColumn("day_number", datediff(col("event_date"), col("signup_date")))

    val playerFlags = activity
      .groupBy("game_id", "player_key", "signup_date", "platform", "build", "country_code")
      .agg(
        max(when(col("day_number") === 1, 1).otherwise(0)).as("retained_d1"),
        max(when(col("day_number") === 7, 1).otherwise(0)).as("retained_d7"),
        max(when(col("day_number") === 30, 1).otherwise(0)).as("retained_d30")
      )

    val cohort = playerFlags
      .groupBy(
        col("game_id"),
        col("signup_date").as("cohort_date"),
        col("platform"),
        col("build"),
        col("country_code")
      )
      .agg(
        countDistinct("player_key").as("cohort_size"),
        sum("retained_d1").as("retained_d1_players"),
        sum("retained_d7").as("retained_d7_players"),
        sum("retained_d30").as("retained_d30_players")
      )
      .withColumn("d1_retention", safeRate(col("retained_d1_players"), col("cohort_size")))
      .withColumn("d7_retention", safeRate(col("retained_d7_players"), col("cohort_size")))
      .withColumn("d30_retention", safeRate(col("retained_d30_players"), col("cohort_size")))

    PrivacyTransforms.suppressSmallGroups(cohort, col("cohort_size"), MinimumPrivacyGroup)
  }

  def buildProgressionFunnel(events: DataFrame): DataFrame = {
    val progression = events
      .filter(col("category") === "progression")
      .filter(col("progression_01").isNotNull)
      .groupBy(
        col("game_id"),
        col("event_date"),
        col("platform"),
        col("build"),
        col("country_code"),
        col("progression_01").as("level")
      )
      .agg(
        countDistinct(when(col("progression_status") === "start", col("player_key"))).as("players_started"),
        countDistinct(when(col("progression_status") === "fail", col("player_key"))).as("players_failed"),
        countDistinct(when(col("progression_status") === "complete", col("player_key"))).as("players_completed")
      )
      .withColumn("completion_rate", safeRate(col("players_completed"), col("players_started")))

    PrivacyTransforms.suppressSmallGroups(progression, col("players_started"), MinimumPrivacyGroup)
  }

  def buildEconomyFlows(events: DataFrame): DataFrame = {
    val flows = events
      .filter(col("category") === "resource")
      .groupBy(
        col("game_id"),
        col("event_date"),
        col("platform"),
        col("build"),
        col("country_code"),
        col("virtual_currency"),
        col("item_type")
      )
      .agg(
        countDistinct(col("player_key")).as("supporting_players"),
        sum(when(col("flow_type") === "source", coalesce(col("amount"), lit(0L))).otherwise(lit(0L))).as("currency_source"),
        sum(when(col("flow_type") === "sink", coalesce(col("amount"), lit(0L))).otherwise(lit(0L))).as("currency_sink")
      )
      .withColumn("net_currency_flow", col("currency_source") - col("currency_sink"))

    PrivacyTransforms.suppressSmallGroups(flows, col("supporting_players"), MinimumPrivacyGroup)
  }

  private def safeRate(numerator: Column, denominator: Column): Column =
    when(denominator > 0, numerator.cast("double") / denominator).otherwise(lit(0.0))

  def main(args: Array[String]): Unit = {
    val catalog = sys.env.getOrElse("ICEBERG_CATALOG", "game")
    val warehouse = sys.env.getOrElse("ICEBERG_WAREHOUSE", "hdfs://localhost:9000/warehouse")

    val spark = SparkSession.builder()
      .appName("build-player-analytics")
      .config(s"spark.sql.catalog.$catalog", "org.apache.iceberg.spark.SparkCatalog")
      .config(s"spark.sql.catalog.$catalog.type", "hadoop")
      .config(s"spark.sql.catalog.$catalog.warehouse", warehouse)
      .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .config("spark.sql.session.timeZone", "UTC")
      .getOrCreate()

    val rawTable = sys.env.getOrElse("RAW_EVENT_TABLE", s"$catalog.raw.game_events")
    val events = spark.table(rawTable)
    val sessions = buildSessions(events)

    sessions.writeTo(s"$catalog.analytics.player_sessions").overwritePartitions()
    buildDailyEngagement(sessions, events).writeTo(s"$catalog.analytics.daily_engagement").overwritePartitions()
    buildRetention(events).writeTo(s"$catalog.analytics.cohort_retention").overwritePartitions()
    buildProgressionFunnel(events).writeTo(s"$catalog.analytics.level_progression").overwritePartitions()
    buildEconomyFlows(events).writeTo(s"$catalog.analytics.economy_flows").overwritePartitions()

    spark.stop()
  }
}
