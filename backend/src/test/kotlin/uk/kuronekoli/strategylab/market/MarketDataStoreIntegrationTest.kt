package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant
import java.time.Clock
import java.time.ZoneId
import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = ["app.market-data.ingestion-enabled=false"])
@ActiveProfiles("local")
class MarketDataStoreIntegrationTest {
    @Autowired private lateinit var store: MarketDataStore
    @Autowired private lateinit var bars: DailyMarketBarRepository
    @Autowired private lateinit var runs: MarketDataIngestionRunRepository

    @Test
    fun `snapshot upsert is idempotent and failed refresh leaves last good rows`() {
        val date = LocalDate.of(2040, 2, 3)
        val snapshot = MarketDataSnapshot(
            GovernmentOpenDataDailySource.METADATA, date, Instant.parse("2040-02-03T12:00:00Z"),
            listOf(
                SourceMarketBar("99101", "fixture", date, d("10"), d("12"), d("9"), d("11"), 100),
                SourceMarketBar("99102", "fixture", date, d("20"), d("22"), d("19"), d("21"), 200),
            ),
        )

        assertEquals(IngestionResult(2, 0), store.saveSnapshot(snapshot))
        val corrected = snapshot.copy(
            fetchedAt = Instant.parse("2040-02-03T13:00:00Z"),
            bars = snapshot.bars.map { it.copy(close = it.close.add(BigDecimal.ONE)) },
        )
        assertEquals(IngestionResult(0, 2), store.saveSnapshot(corrected))
        val result = store.findBars("99101", date, date)
        assertNotNull(result)
        assertEquals(0, BigDecimal("12").compareTo(result!!.bars.single().close))
        assertEquals(date, result.earliestAvailableDate)
        assertEquals(2, bars.findAllByTradingDateAndAdjustmentPolicy(date, "RAW").size)

        store.recordFailure(snapshot.metadata.sourceId, Instant.parse("2040-02-04T12:00:00Z"), Instant.parse("2040-02-04T12:00:01Z"), "UPSTREAM_DATA_UNAVAILABLE")
        assertEquals(0, BigDecimal("12").compareTo(store.findBars("99101", date, date)!!.bars.single().close))
        assertEquals("FAILED", runs.findTopByOrderByStartedAtDesc()!!.status)
    }

    @Test
    fun `historical import migrates selected source bars and retains source rights status`() {
        val date = LocalDate.of(2033, 5, 9)
        val government = MarketDataSnapshot(
            GovernmentOpenDataDailySource.METADATA, date, Instant.parse("2033-05-09T12:00:00Z"),
            listOf(SourceMarketBar("99150", "fixture", date, d("10"), d("12"), d("9"), d("11"), 100)),
        )
        store.saveSnapshot(government)
        val imported = HistoricalMarketDataImport(
            metadata = FugleHistoricalMarketDataSource.METADATA,
            symbol = "99150", from = date.minusDays(1), to = date.plusDays(1),
            fetchedAt = Instant.parse("2033-05-10T12:00:00Z"),
            bars = listOf(SourceMarketBar("99150", "fixture", date, d("20"), d("22"), d("19"), d("21"), 200)),
        )

        assertEquals(IngestionResult(0, 1), store.saveHistory(imported))
        val stored = store.findBars("99150", date, date)!!
        assertEquals("fugle-taiwan-stock-historical-candles", stored.metadata.sourceId)
        assertEquals("CONFIRMED", stored.metadata.licensingStatus)
        assertEquals(0, BigDecimal("21").compareTo(stored.bars.single().close))
        assertEquals(1, bars.findAllByTradingDateAndAdjustmentPolicy(date, "RAW").count { it.symbol == "99150" })
    }

    @Test
    fun `a mixed confirmed and unverified source range is not exposed`() {
        val finMindDate = LocalDate.of(2035, 6, 2)
        val governmentDate = finMindDate.plusDays(1)
        store.saveHistory(HistoricalMarketDataImport(
            FinMindHistoricalMarketDataSource.METADATA, "99151", finMindDate, governmentDate,
            Instant.parse("2035-06-04T12:00:00Z"),
            listOf(SourceMarketBar("99151", "fixture", finMindDate, d("10"), d("12"), d("9"), d("11"), 100)),
        ))
        store.saveSnapshot(MarketDataSnapshot(
            GovernmentOpenDataDailySource.METADATA, governmentDate, Instant.parse("2035-06-04T13:00:00Z"),
            listOf(SourceMarketBar("99151", "fixture", governmentDate, d("11"), d("13"), d("10"), d("12"), 120)),
        ))

        val result = DatabaseHistoricalMarketDataProvider(store, allowUnverifiedDisplay = false)
            .loadSnapshot("99151", YearMonth.from(finMindDate), YearMonth.from(governmentDate))
        assertEquals("UNVERIFIED", result.licensingStatus)
        assertEquals(emptyList<DailyBar>(), result.bars)
    }

    @Test
    fun `imported multi-day history reaches the stock chart response as multiple daily candles`() {
        val dates = listOf(LocalDate.of(2036, 1, 2), LocalDate.of(2036, 1, 3), LocalDate.of(2036, 1, 4))
        store.saveHistory(HistoricalMarketDataImport(
            FinMindHistoricalMarketDataSource.METADATA, "99152", dates.first(), dates.last(),
            Instant.parse("2036-01-05T12:00:00Z"), dates.mapIndexed { index, date ->
                val price = BigDecimal("${10 + index}")
                SourceMarketBar("99152", "fixture", date, price, price.add(d("1")), price.subtract(d("1")), price, 100L + index)
            },
        ))
        val provider = DatabaseHistoricalMarketDataProvider(store, allowUnverifiedDisplay = true)
        val service = StockHistoryService(provider, Clock.fixed(Instant.parse("2036-01-06T00:00:00Z"), ZoneId.of("UTC")), true)

        val history = service.history("99152", dates.first().toString(), dates.last().toString(), "1d")

        assertEquals(3, history.bars.size)
        assertEquals("UNVERIFIED", history.licensingStatus)
    }

    private fun d(value: String) = BigDecimal(value)
}
