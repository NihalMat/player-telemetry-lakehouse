package com.nihal.games.validation

object EventRules {
  val SupportedCategories: Set[String] = Set(
    "user",
    "session_end",
    "business",
    "progression",
    "resource",
    "design",
    "error",
    "ad",
    "impression"
  )

  val ProgressionStatuses: Set[String] = Set("start", "fail", "complete")

  def isSupportedCategory(category: String): Boolean =
    Option(category).exists(value => SupportedCategories.contains(value.toLowerCase))

  def isValidProgressionStatus(status: String): Boolean =
    Option(status).exists(value => ProgressionStatuses.contains(value.toLowerCase))
}
