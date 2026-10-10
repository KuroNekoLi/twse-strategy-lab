package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.Executors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseHistoricalMarketDataProviderTest {
    @Test
    fun `missing symbol history is imported then subsequent requests use database coverage`() {
        val store = MemoryStore()
        var fetchCount = 0
        val source = source { symbol, from, to ->
            fetchCount++
            importWeekdays(symbol, from, to)
        }
        val provider = provider(store, source)

        val first = provider.loadSnapshot("2330", YearMonth.of(2025, 1), YearMonth.of(2025, 2))
        val second = provider.loadSnapshot("2330", YearMonth.of(2025, 1), YearMonth.of(2025, 2))

        assertTrue(first.bars.size > 20)
        assertEquals(first.bars, second.bars)
        assertEquals(1, fetchCount)
        assertEquals(1, store.coverage.size)
    }

    @Test
    fun `partial symbol coverage only backfills the missing date range`() {
        val store = MemoryStore().apply {
            coverage += HistoricalCoverage("0050", LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-31"), Instant.EPOCH)
        }
        val fetchedRanges = mutableListOf<Pair<LocalDate, LocalDate>>()
        val source = source { symbol, from, to ->
            fetchedRanges += from to to
            importWeekdays(symbol, from, to)
        }

        val result = provider(store, source).loadSnapshot("0050", YearMonth.of(2025, 1), YearMonth.of(2025, 2))

        assertTrue(result.bars.isNotEmpty())
        assertEquals(listOf(LocalDate.parse("2025-02-01") to LocalDate.parse("2025-02-28")), fetchedRanges)
    }

    @Test
    fun `concurrent identical requests trigger a single backfill in this instance`() {
        val store = MemoryStore()
        var fetchCount = 0
        val source = source { symbol, from, to ->
            synchronized(this) { fetchCount++ }
            Thread.sleep(100)
            importWeekdays(symbol, from, to)
        }
        val provider = provider(store, source)
        val pool = Executors.newFixedThreadPool(6)

        try {
            val futures = (1..6).map {
                pool.submit<List<DailyBar>> { provider.loadSnapshot("2330", YearMonth.of(2025, 1), YearMonth.of(2025, 2)).bars }
            }
            futures.forEach { assertTrue(it.get().isNotEmpty()) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, fetchCount)
        assertEquals(1, store.coverage.size)
    }

    private fun provider(store: MemoryStore, source: HistoricalMarketDataSource) =
        DatabaseHistoricalMarketDataProvider(store, allowUnverifiedDisplay = false, backfill = MarketDataHistoryBackfillService(source, store))

    private fun source(fetch: (String, LocalDate, LocalDate) -> HistoricalMarketDataImport) = object : HistoricalMarketDataSource {
        override val metadata = FugleHistoricalMarketDataSource.METADATA
        override fun fetchHistory(symbol: String, from: LocalDate, to: LocalDate) = fetch(symbol, from, to)
    }

    private fun importWeekdays(symbol: String, from: LocalDate, to: LocalDate): HistoricalMarketDataImport {
        val bars = generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek.value <= 5 }
            .mapIndexed { index, date ->
                val price = BigDecimal("${100 + index}")
                SourceMarketBar(symbol, symbol, date, price, price.add(BigDecimal.ONE), price.subtract(BigDecimal.ONE), price, 1000L)
            }.toList()
        return HistoricalMarketDataImport(FugleHistoricalMarketDataSource.METADATA, symbol, from, to, Instant.parse("2025-03-01T00:00:00Z"), bars)
    }

    private class MemoryStore : MarketDataStore {
        private val bars = mutableMapOf<String, MutableList<SourceMarketBar>>()
        val coverage = mutableListOf<HistoricalCoverage>()
        private val metadata = mutableMapOf<String, MarketDataSourceMetadata>()

        override fun findBars(symbol: String, from: LocalDate, to: LocalDate): StoredMarketBars? {
            val selected = bars[symbol].orEmpty().filter { it.date in from..to }.sortedBy(SourceMarketBar::date)
            if (selected.isEmpty()) return null
            val sourceMetadata = metadata.getValue(symbol)
            return StoredMarketBars(symbol, selected.map(SourceMarketBar::toDailyBar), sourceMetadata, Instant.EPOCH, Instant.EPOCH, selected.first().date)
        }

        override fun findEarliestDate(symbol: String): LocalDate? = bars[symbol]?.minOfOrNull(SourceMarketBar::date)
        override fun findHistoricalCoverage(symbol: String, from: LocalDate, to: LocalDate) =
            coverage.filter { it.symbol == symbol && it.from <= to && it.to >= from }.sortedBy(HistoricalCoverage::from)
        override fun saveSnapshot(snapshot: MarketDataSnapshot) = error("Not used")
        override fun saveHistory(import: HistoricalMarketDataImport): IngestionResult {
            val existing = bars.getOrPut(import.symbol) { mutableListOf() }
            import.bars.forEach { bar ->
                existing.removeIf { it.date == bar.date }
                existing += bar
            }
            metadata[import.symbol] = import.metadata
            coverage += HistoricalCoverage(import.symbol, import.from, import.to, import.fetchedAt)
            return IngestionResult(import.bars.size, 0)
        }
        override fun recordFailure(sourceId: String, startedAt: Instant, finishedAt: Instant, errorCode: String) = Unit
    }
}
