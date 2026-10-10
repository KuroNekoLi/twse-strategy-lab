package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
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
        val date = LocalDate.of(2032, 2, 3)
        val snapshot = MarketDataSnapshot(
            GovernmentOpenDataDailySource.METADATA, date, Instant.parse("2032-02-03T12:00:00Z"),
            listOf(
                SourceMarketBar("99101", "fixture", date, d("10"), d("12"), d("9"), d("11"), 100),
                SourceMarketBar("99102", "fixture", date, d("20"), d("22"), d("19"), d("21"), 200),
            ),
        )

        assertEquals(IngestionResult(2, 0), store.saveSnapshot(snapshot))
        val corrected = snapshot.copy(
            fetchedAt = Instant.parse("2032-02-03T13:00:00Z"),
            bars = snapshot.bars.map { it.copy(close = it.close.add(BigDecimal.ONE)) },
        )
        assertEquals(IngestionResult(0, 2), store.saveSnapshot(corrected))
        val result = store.findBars("99101", date, date)
        assertNotNull(result)
        assertEquals(0, BigDecimal("12").compareTo(result!!.bars.single().close))
        assertEquals(date, result.earliestAvailableDate)
        assertEquals(2, bars.findAllByTradingDateAndAdjustmentPolicy(date, "RAW").size)

        store.recordFailure(snapshot.metadata.sourceId, Instant.parse("2032-02-04T12:00:00Z"), Instant.parse("2032-02-04T12:00:01Z"), "UPSTREAM_DATA_UNAVAILABLE")
        assertEquals(0, BigDecimal("12").compareTo(store.findBars("99101", date, date)!!.bars.single().close))
        assertEquals("FAILED", runs.findTopByOrderByStartedAtDesc()!!.status)
    }

    private fun d(value: String) = BigDecimal(value)
}
