package com.nihal.games.model

import org.apache.spark.sql.types._

object EventSchema {
  val RawGameEvent: StructType = StructType(
    Seq(
      StructField("event_uuid", StringType, nullable = false),
      StructField("event_time", TimestampType, nullable = false),
      StructField("game_id", StringType, nullable = false),
      StructField("user_id", StringType, nullable = false),
      StructField("session_id", StringType, nullable = false),
      StructField("category", StringType, nullable = false),
      StructField("event_id", StringType, nullable = true),
      StructField("progression_status", StringType, nullable = true),
      StructField("progression_01", StringType, nullable = true),
      StructField("progression_02", StringType, nullable = true),
      StructField("progression_03", StringType, nullable = true),
      StructField("currency", StringType, nullable = true),
      StructField("amount", LongType, nullable = true),
      StructField("item_type", StringType, nullable = true),
      StructField("item_id", StringType, nullable = true),
      StructField("flow_type", StringType, nullable = true),
      StructField("virtual_currency", StringType, nullable = true),
      StructField("severity", StringType, nullable = true),
      StructField("message", StringType, nullable = true),
      StructField("value", DoubleType, nullable = true),
      StructField("platform", StringType, nullable = true),
      StructField("os_version", StringType, nullable = true),
      StructField("build", StringType, nullable = true),
      StructField("country_code", StringType, nullable = true),
      StructField("signup_date", DateType, nullable = true)
    )
  )
}
