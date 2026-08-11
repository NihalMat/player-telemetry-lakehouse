package com.nihal.games.jobs

import com.nihal.games.model.EventSchema
import com.nihal.games.privacy.PrivacyTransforms
import com.nihal.games.validation.EventRules
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger

object IngestGameEvents {
  final case class Config(
      bootstrapServers: String,
      topic: String,
      catalogName: String,
      warehouse: String,
      outputTable: String,
      checkpointLocation: String,
      playerSalt: String
  )

  def main(args: Array[String]): Unit = {
    val config = Config(
      bootstrapServers = sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092"),
      topic = sys.env.getOrElse("GAME_EVENT_TOPIC", "gameplay-events"),
      catalogName = sys.env.getOrElse("ICEBERG_CATALOG", "game"),
      warehouse = sys.env.getOrElse("ICEBERG_WAREHOUSE", "hdfs://localhost:9000/warehouse"),
      outputTable = sys.env.getOrElse("RAW_EVENT_TABLE", "game.raw.game_events"),
      checkpointLocation = sys.env.getOrElse("CHECKPOINT_LOCATION", "hdfs://localhost:9000/checkpoints/game-events"),
      playerSalt = sys.env.getOrElse("PLAYER_HASH_SALT", "replace-in-secret-manager")
    )

    val spark = SparkSession.builder()
      .appName("player-telemetry-ingest")
      .config(s"spark.sql.catalog.${config.catalogName}", "org.apache.iceberg.spark.SparkCatalog")
      .config(s"spark.sql.catalog.${config.catalogName}.type", "hadoop")
      .config(s"spark.sql.catalog.${config.catalogName}.warehouse", config.warehouse)
      .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .config("spark.sql.session.timeZone", "UTC")
      .getOrCreate()

    val kafka = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", config.bootstrapServers)
      .option("subscribe", config.topic)
      .option("startingOffsets", "latest")
      .option("failOnDataLoss", "false")
      .load()

    val parsed = kafka
      .select(from_json(col("value").cast("string"), EventSchema.RawGameEvent).as("event"))
      .select("event.*")
      .withColumn("category", lower(trim(col("category"))))

    val validEvents = parsed
      .filter(col("event_uuid").isNotNull)
      .filter(col("event_time").isNotNull)
      .filter(col("game_id").isNotNull)
      .filter(col("user_id").isNotNull)
      .filter(col("session_id").isNotNull)
      .filter(col("category").isin(EventRules.SupportedCategories.toSeq: _*))
      .withColumn("event_date", to_date(col("event_time")))
      .withColumn("ingested_at", current_timestamp())
      .withWatermark("event_time", "24 hours")
      .dropDuplicates("event_uuid")

    val protectedEvents = PrivacyTransforms.pseudonymizePlayerId(validEvents, config.playerSalt)

    protectedEvents.writeStream
      .format("iceberg")
      .outputMode("append")
      .trigger(Trigger.ProcessingTime("1 minute"))
      .option("checkpointLocation", config.checkpointLocation)
      .option("fanout-enabled", "true")
      .toTable(config.outputTable)
      .awaitTermination()
  }
}
