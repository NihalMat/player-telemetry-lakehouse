package com.nihal.games

import com.nihal.games.validation.EventRules
import org.scalatest.funsuite.AnyFunSuite

class EventRulesSpec extends AnyFunSuite {
  test("supported game analytics categories are accepted case insensitively") {
    assert(EventRules.isSupportedCategory("Progression"))
    assert(EventRules.isSupportedCategory("business"))
    assert(!EventRules.isSupportedCategory("unknown"))
  }

  test("progression status is restricted to start fail or complete") {
    assert(EventRules.isValidProgressionStatus("start"))
    assert(EventRules.isValidProgressionStatus("Complete"))
    assert(!EventRules.isValidProgressionStatus("pause"))
  }
}
