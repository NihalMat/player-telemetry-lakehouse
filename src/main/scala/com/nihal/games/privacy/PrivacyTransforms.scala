package com.nihal.games.privacy

import org.apache.spark.sql.{Column, DataFrame}
import org.apache.spark.sql.functions._

object PrivacyTransforms {
  def pseudonymizePlayerId(events: DataFrame, salt: String): DataFrame =
    events
      .withColumn(
        "player_key",
        sha2(concat(lit(salt), coalesce(col("user_id"), lit("missing"))), 256)
      )
      .drop("user_id")

  def suppressSmallGroups(
      aggregates: DataFrame,
      groupSize: Column,
      minimumGroupSize: Int = 20
  ): DataFrame = aggregates.filter(groupSize >= lit(minimumGroupSize))
}
