package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import uk.kuronekoli.strategylab.api.BacktestException

class MarketDataHistoryBackfillTest {
    @Test
    fun `refuses to persist a one-row response for a five-year backfill`() {
        var saved = false
        val source = object : HistoricalMarketDataSource {
            override val metadata = FugleHistoricalMarketDataSource.METADATA
            override fun fetchHistory(symbol: String, from: LocalDate, to: LocalDate) = HistoricalMarketDataImport(
                metadata = FugleHistoricalMarketDataSource.METADATA,
                symbol = symbol,
                from = from,
                to = to,
                fetchedAt = Instant.parse("2026-10-10T00:00:00Z"),
                bars = listOf(SourceMarketBar(symbol, symbol, LocalDate.parse("2026-10-08"), bd("100"), bd("101"), bd("99"), bd("100"), 1)),
            )
        }
        val store = object : MarketDataStore {
            override fun findBars(symbol: String, from: LocalDate, to: LocalDate): StoredMarketBars? = null
            override fun findEarliestDate(symbol: String): LocalDate? = null
            override fun findHistoricalCoverage(symbol: String, from: LocalDate, to: LocalDate) = emptyList<HistoricalCoverage>()
            override fun saveSnapshot(snapshot: MarketDataSnapshot) = error("not used")
            override fun saveHistory(import: HistoricalMarketDataImport): IngestionResult { saved = true; return IngestionResult(1, 0) }
            override fun recordFailure(sourceId: String, startedAt: Instant, finishedAt: Instant, errorCode: String) = Unit
        }

        val error = assertThrows(BacktestException::class.java) {
            MarketDataHistoryBackfillService(source, store).import("0050", LocalDate.parse("2021-10-10"), LocalDate.parse("2026-10-10"), minimumRows = 1000)
        }
        assertEquals("DATA_COVERAGE_INSUFFICIENT", error.code)
        assertEquals(false, saved)
    }

    private fun bd(value: String) = BigDecimal(value)
}
