package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.api.BacktestException

/** Offline synthetic JSON only; these tests do not establish live provider coverage. */
class TwseMarketDataClientTest {
    private val mapper = JsonMapper.builder().build()
    private val client = TwseMarketDataClient(mapper, "https://example.invalid/STOCK_DAY")
    private val month = YearMonth.of(2024, 1)
    private fun row(date: String, price: String) = "[\"$date\",\"1,234\",\"12,340\",\"$price\",\"$price\",\"$price\",\"$price\",\"0\",\"12\"]"
    @Test fun rocDatesAndCommaDecimalPricesAreParsedExactly() {
        val bars = client.parseMonthResponse("2330", month, mapper.readTree("{\"stat\":\"OK\",\"data\":[${row("113/01/02", "1,000.50")},${row("113/01/03", "1001.00")}]}"))
        assertEquals("2024-01-02", bars[0].date.toString()); assertEquals(0, BigDecimal("1000.50").compareTo(bars[0].close)); assertEquals(0, BigDecimal("1000.50").compareTo(bars[0].open)); assertEquals(1234L, bars[0].volume); assertTrue(bars[0].hasOhlcv); assertEquals(2, bars.size)
    }
    @Test fun emptyMissingAndNonOkMonthlyResponsesNeverBecomeSilentEmptySuccess() {
        listOf("{}", "null", "[]", "{\"stat\":\"查無資料\"}", "{\"stat\":\"OK\"}", "{\"stat\":\"OK\",\"data\":[]}", "{\"stat\":\"OK\",\"data\":{}}").forEach { response ->
            val error = assertThrows(BacktestException::class.java) { client.parseMonthResponse("2330", month, mapper.readTree(response)) }
            assertTrue(error.code.startsWith("UPSTREAM_")); assertEquals(502, error.status); assertTrue(error.message!!.contains("2330／2024-01"))
        }
    }
    @Test fun malformedRowsIncludingUnknownSuspensionPriceStopTheWholeMonth() {
        listOf("[]", row("bad", "100"), row("113/02/01", "100"), row("113/01/32", "100"), row("113/01/03", "--"), row("113/01/03", "0"), row("113/01/03", "NaN"), row("113/01/03", "10,00"), "[\"113/01/03\",\"-1\",\"0\",\"100\",\"100\",\"100\",\"100\",\"0\",\"1\"]").forEach { bad ->
            val body = "{\"stat\":\"OK\",\"data\":[${row("113/01/02", "100")},$bad]}"
            assertEquals("DATA_INTEGRITY_FAILED", assertThrows(BacktestException::class.java) { client.parseMonthResponse("2330", month, mapper.readTree(body)) }.code)
        }
    }
    @Test fun inconsistentOhlcValuesAndMissingVolumeStopTheWholeMonth() {
        val invalidHigh = "[\"113/01/02\",\"100\",\"10000\",\"100\",\"99\",\"98\",\"100\",\"0\",\"1\"]"
        val missingVolume = "[\"113/01/02\",\"--\",\"10000\",\"100\",\"101\",\"99\",\"100\",\"0\",\"1\"]"
        listOf(invalidHigh, missingVolume).forEach { invalid ->
            val body = "{\"stat\":\"OK\",\"data\":[$invalid]}"
            assertEquals("DATA_INTEGRITY_FAILED", assertThrows(BacktestException::class.java) { client.parseMonthResponse("2330", month, mapper.readTree(body)) }.code)
        }
    }
    @Test fun duplicateAndUnorderedDatesAreRejectedRatherThanSortedOrDeduplicated() {
        listOf(row("113/01/02", "100") + "," + row("113/01/02", "100"), row("113/01/03", "100") + "," + row("113/01/02", "100")).forEach { rows ->
            val body = "{\"stat\":\"OK\",\"data\":[$rows]}"
            assertEquals("DATA_INTEGRITY_FAILED", assertThrows(BacktestException::class.java) { client.parseMonthResponse("2330", month, mapper.readTree(body)) }.code)
        }
    }
}
