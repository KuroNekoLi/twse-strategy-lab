package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarketDataIngestionServiceTest {
    @Test
    fun `validates a full market snapshot independently per symbol`() {
        val date = LocalDate.of(2026, 10, 8)
        val metadata = GovernmentOpenDataDailySource.METADATA
        val snapshot = MarketDataSnapshot(
            metadata, date, Instant.parse("2026-10-08T12:00:00Z"),
            listOf(
                SourceMarketBar("0050", "元大台灣50", date, d("100"), d("102"), d("99"), d("101"), 1000),
                SourceMarketBar("2330", "台積電", date, d("1200"), d("1220"), d("1190"), d("1210"), 2000),
            ),
        )
        val store = RecordingStore()
        val service = MarketDataIngestionService(object : DailyMarketDataSource {
            override fun fetchLatestSnapshot() = snapshot
        }, store, true)

        assertEquals(IngestionResult(2, 0), service.refreshLatest())
        assertEquals(snapshot, store.saved)
    }

    private class RecordingStore : MarketDataStore {
        var saved: MarketDataSnapshot? = null
        override fun findBars(symbol: String, from: LocalDate, to: LocalDate): StoredMarketBars? = null
        override fun findEarliestDate(symbol: String): LocalDate? = null
        override fun saveSnapshot(snapshot: MarketDataSnapshot): IngestionResult { saved = snapshot; return IngestionResult(snapshot.bars.size, 0) }
        override fun saveHistory(import: HistoricalMarketDataImport): IngestionResult = error("Not used by this test")
        override fun recordFailure(sourceId: String, startedAt: Instant, finishedAt: Instant, errorCode: String) = Unit
    }

    private fun d(value: String) = BigDecimal(value)
}
