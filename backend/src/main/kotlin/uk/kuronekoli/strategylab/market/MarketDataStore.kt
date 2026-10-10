package uk.kuronekoli.strategylab.market

import java.time.Instant
import java.time.LocalDate

data class IngestionResult(val inserted: Int, val updated: Int, val rejected: Int = 0)

interface MarketDataStore {
    fun findBars(symbol: String, from: LocalDate, to: LocalDate): StoredMarketBars?
    fun findEarliestDate(symbol: String): LocalDate?
    fun saveSnapshot(snapshot: MarketDataSnapshot): IngestionResult
    fun recordFailure(sourceId: String, startedAt: Instant, finishedAt: Instant, errorCode: String)
}
