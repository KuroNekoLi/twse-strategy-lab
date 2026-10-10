package uk.kuronekoli.strategylab.market

import java.time.Instant
import java.time.LocalDate

data class IngestionResult(val inserted: Int, val updated: Int, val rejected: Int = 0, val skippedSourceConflict: Int = 0)
data class HistoricalCoverage(val symbol: String, val from: LocalDate, val to: LocalDate, val verifiedAt: Instant)

interface MarketDataStore {
    fun findBars(symbol: String, from: LocalDate, to: LocalDate): StoredMarketBars?
    fun findEarliestDate(symbol: String): LocalDate?
    fun findHistoricalCoverage(symbol: String, from: LocalDate, to: LocalDate): List<HistoricalCoverage>
    fun saveSnapshot(snapshot: MarketDataSnapshot): IngestionResult
    fun saveHistory(import: HistoricalMarketDataImport): IngestionResult
    fun recordFailure(sourceId: String, startedAt: Instant, finishedAt: Instant, errorCode: String)
}
