package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import uk.kuronekoli.strategylab.api.BacktestException

class StockHistoryServiceTest {
    private val taipei = ZoneId.of("Asia/Taipei")

    @Test
    fun `disabled history gate returns 503 without calling the market source`() {
        val source = mock(TwseMarketDataClient::class.java)
        val service = StockHistoryService(source, fixedClock("2026-10-09T00:00:00Z"), false)

        val error = assertThrows(BacktestException::class.java) {
            service.history("2330", "2025-01-01", "2025-10-01")
        }

        assertEquals("MARKET_HISTORY_DISABLED", error.code)
        assertEquals(503, error.status)
        verifyNoInteractions(source)
    }

    @Test
    fun `omitted interval preserves daily dates and filters provider observations to requested dates`() {
        val provider = FixtureProvider(
            listOf(
                bar("2025-01-01", "90"),
                bar("2025-01-02", "100"),
                bar("2025-01-03", "101"),
                bar("2025-01-04", "110"),
            ),
        )
        val response = service(provider, "2025-01-03T12:00:00Z")
            .history("0050", "2025-01-02", "2025-01-03")

        assertEquals("1d", response.interval)
        assertEquals("2025-01-02", response.observedFrom)
        assertEquals("2025-01-03", response.observedTo)
        assertEquals(listOf("2025-01-02", "2025-01-03"), response.bars.map { it.date })
        assertEquals(listOf("ELAPSED", "IN_PROGRESS"), response.bars.map { it.periodWindowStatus })
        assertEquals(listOf("UNKNOWN", "UNKNOWN"), response.bars.map { it.coverageStatus })
    }

    @Test
    fun `weekly buckets use monday across month and year boundaries and aggregate complete ohlcv`() {
        val provider = FixtureProvider(
            listOf(
                ohlcv("2023-12-29", "10", "12", "9", "11", 100),
                ohlcv("2024-01-02", "11", "14", "10", "13", 200),
                ohlcv("2024-01-03", "12", "13", "11", "12", 250),
                ohlcv("2024-01-08", "13", "15", "12", "14", 300),
            ),
        )
        val response = service(provider, "2024-01-10T00:00:00Z")
            .history("0050", "2023-12-29", "2024-01-09", "1w")

        assertEquals(listOf("2023-12-25", "2024-01-01", "2024-01-08"), response.bars.map { it.date })
        val firstWeek = response.bars[0]
        assertEquals("2023-12-25", firstWeek.periodStart)
        assertEquals("2023-12-31", firstWeek.periodEnd)
        assertEquals("2023-12-29", firstWeek.observedFrom)
        assertEquals("2023-12-29", firstWeek.observedTo)
        assertEquals("CLIPPED_BY_REQUEST", firstWeek.periodWindowStatus)
        val yearCrossingWeek = response.bars[1]
        assertEquals("2024-01-01", yearCrossingWeek.periodStart)
        assertEquals("2024-01-07", yearCrossingWeek.periodEnd)
        assertEquals("2023-12-29", response.observedFrom)
        assertEquals("2024-01-08", response.observedTo)
        assertEquals("11", yearCrossingWeek.open)
        assertEquals("14", yearCrossingWeek.high)
        assertEquals("10", yearCrossingWeek.low)
        assertEquals("12", yearCrossingWeek.close)
        assertEquals(450L, yearCrossingWeek.volume)
        assertEquals("UNKNOWN", yearCrossingWeek.coverageStatus)
    }

    @Test
    fun `monthly bars use calendar month first day and report requested clipping`() {
        val provider = FixtureProvider(
            listOf(
                ohlcv("2025-01-15", "10", "12", "9", "11", 100),
                ohlcv("2025-01-31", "11", "14", "10", "13", 200),
                ohlcv("2025-02-03", "13", "15", "12", "14", 300),
            ),
        )
        val response = service(provider, "2025-02-03T12:00:00Z")
            .history("0050", "2025-01-15", "2025-02-03", "1mo")

        assertEquals(listOf("2025-01-01", "2025-02-01"), response.bars.map { it.date })
        assertEquals("2025-01-31", response.bars[0].periodEnd)
        assertEquals("CLIPPED_BY_REQUEST", response.bars[0].periodWindowStatus)
        assertEquals("2025-02-28", response.bars[1].periodEnd)
        assertEquals("IN_PROGRESS", response.bars[1].periodWindowStatus)
        assertEquals("2025-01-15", response.bars[0].observedFrom)
        assertEquals("2025-01-31", response.bars[0].observedTo)
    }

    @Test
    fun `partial ohlcv cannot invent values while preserving last close`() {
        val closeOnly = FixtureProvider(
            listOf(
                bar("2025-03-03", "10"),
                bar("2025-03-04", "12"),
            ),
        )

        val response = service(closeOnly, "2025-03-05T00:00:00Z")
            .history("0050", "2025-03-03", "2025-03-04", "1w")

        val weekly = response.bars.single()
        assertEquals("12", weekly.close)
        assertNull(weekly.open)
        assertNull(weekly.high)
        assertNull(weekly.low)
        assertNull(weekly.volume)
    }

    @Test
    fun `missing volume makes aggregate ohlcv unavailable`() {
        val provider = FixtureProvider(
            listOf(
                ohlcv("2025-04-01", "10", "12", "9", "11", 100),
                bar("2025-04-02", "12"),
            ),
        )

        val weekly = service(provider, "2025-04-03T00:00:00Z")
            .history("0050", "2025-04-01", "2025-04-02", "1w").bars.single()

        assertEquals("12", weekly.close)
        assertNull(weekly.open)
        assertNull(weekly.high)
        assertNull(weekly.low)
        assertNull(weekly.volume)
    }

    @Test
    fun `current week ending today is marked in progress`() {
        val provider = FixtureProvider(listOf(bar("2025-05-07", "10"), bar("2025-05-09", "11")))

        val weekly = service(provider, "2025-05-09T12:00:00Z")
            .history("0050", "2025-05-05", "2025-05-09", "1w").bars.single()

        assertEquals("IN_PROGRESS", weekly.periodWindowStatus)
        assertEquals("2025-05-05", weekly.periodStart)
        assertEquals("2025-05-11", weekly.periodEnd)
    }

    @Test
    fun `invalid interval uses stable invalid input response`() {
        val error = assertThrows(BacktestException::class.java) {
            service(FixtureProvider(listOf(bar("2025-01-02", "10"))), "2025-01-03T00:00:00Z")
                .history("0050", "2025-01-02", "2025-01-02", "1wk")
        }

        assertEquals("INVALID_INPUT", error.code)
        assertEquals(400, error.status)
    }

    private fun service(provider: HistoricalMarketDataProvider, instant: String) =
        StockHistoryService(provider, fixedClock(instant), true)

    private fun fixedClock(instant: String): Clock = Clock.fixed(Instant.parse(instant), taipei)

    private fun bar(date: String, close: String) = DailyBar(LocalDate.parse(date), BigDecimal(close))

    private fun ohlcv(date: String, open: String, high: String, low: String, close: String, volume: Long) = DailyBar(
        LocalDate.parse(date), BigDecimal(close), BigDecimal(open), BigDecimal(high), BigDecimal(low), volume,
    )

    private class FixtureProvider(private val bars: List<DailyBar>) : HistoricalMarketDataProvider {
        override val sourceName: String = "fixture"
        override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> = bars
    }
}
