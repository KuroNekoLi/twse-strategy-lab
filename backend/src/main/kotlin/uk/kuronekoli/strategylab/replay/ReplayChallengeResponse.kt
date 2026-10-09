package uk.kuronekoli.strategylab.replay

import java.util.Collections

import java.math.BigDecimal
import java.time.LocalDate

/** Allow-listed response DTOs: no private source bars or future observations are serialized. */
class ReplayChallengeResponse(val sessionId: String, val challenge: Challenge, val observation: Observation?, val state: Portfolio, events: List<DecisionEvent>) {
    val events: List<DecisionEvent> = Collections.unmodifiableList(events.toList())
    data class Challenge(val id: String, val title: String, val label: String, val notice: String)
    data class Observation(val date: LocalDate, val close: BigDecimal)
    data class Portfolio(val cash: BigDecimal, val shares: BigDecimal, val decisionCount: Int, val complete: Boolean)
    data class DecisionEvent(val date: LocalDate, val observedClose: BigDecimal, val action: String, val shares: BigDecimal, val cashAfter: BigDecimal, val sharesAfter: BigDecimal)
}
