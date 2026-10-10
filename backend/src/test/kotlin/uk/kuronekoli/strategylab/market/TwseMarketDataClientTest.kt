package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import javax.net.ssl.SSLSession
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
    private fun response(request: HttpRequest, status: Int, body: String = "") = object : HttpResponse<String> {
        override fun statusCode() = status
        override fun request() = request
        override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()
        override fun headers() = HttpHeaders.of(emptyMap<String, List<String>>()) { _, _ -> true }
        override fun body() = body
        override fun sslSession(): Optional<SSLSession> = Optional.empty()
        override fun uri() = request.uri()
        override fun version() = HttpClient.Version.HTTP_1_1
    }
    @Test fun rocDatesAndCommaDecimalPricesAreParsedExactly() {
        val bars = client.parseMonthResponse("2330", month, mapper.readTree("{\"stat\":\"OK\",\"data\":[${row("113/01/02", "1,000.50")},${row("113/01/03", "1001.00")}]}"))
        assertEquals("2024-01-02", bars[0].date.toString()); assertEquals(0, BigDecimal("1000.50").compareTo(bars[0].close)); assertEquals(0, BigDecimal("1000.50").compareTo(bars[0].open)); assertEquals(1234L, bars[0].volume); assertTrue(bars[0].hasOhlcv); assertEquals(2, bars.size)
    }
    @Test fun emptyMissingAndNonOkMonthlyResponsesNeverBecomeSilentEmptySuccess() {
        listOf("{}", "null", "[]", "{\"stat\":\"查無資料\"}", "{\"stat\":\"OK\"}", "{\"stat\":\"OK\",\"data\":{}}").forEach { response ->
            val error = assertThrows(BacktestException::class.java) { client.parseMonthResponse("2330", month, mapper.readTree(response)) }
            assertEquals("UPSTREAM_DATA_UNAVAILABLE", error.code); assertEquals(502, error.status); assertTrue(error.message!!.contains("2330／2024-01"))
        }
        val emptyMonth = assertThrows(BacktestException::class.java) {
            client.parseMonthResponse("2330", month, mapper.readTree("{\"stat\":\"OK\",\"data\":[]}"))
        }
        assertEquals("UPSTREAM_DATA_UNAVAILABLE", emptyMonth.code)
        assertEquals(502, emptyMonth.status)
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
    @Test fun missingLocationFallbackStaysOnTwseAndPreservesMonthlyQuery() {
        val original = URI.create("https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=20230701&stockNo=0050&response=json")
        assertEquals("https://www.twse.com.tw/exchangeReport/STOCK_DAY?date=20230701&stockNo=0050&response=json", client.missingLocationFallback(original).toString())
        assertNull(client.missingLocationFallback(URI.create("https://other.example/rwd/zh/afterTrading/STOCK_DAY?date=20230701&stockNo=0050&response=json")))
        assertNull(client.missingLocationFallback(URI.create("https://www.twse.com.tw/exchangeReport/STOCK_DAY?date=20230701&stockNo=0050&response=json")))
    }
    @Test fun fetchRetriesLocationless307ThenCallsSameHostFallbackWithOriginalQuery() {
        val original = URI.create("https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=20230701&stockNo=0050&response=json")
        val fallback = client.missingLocationFallback(original)!!
        val requests = mutableListOf<URI>()
        val result = client.fetch(original) { request ->
            requests += request.uri()
            response(request, if (requests.size <= 3) 307 else 200, "{\"stat\":\"OK\"}")
        }
        assertEquals(200, result.statusCode())
        assertEquals(listOf(original, original, original, fallback), requests)
    }
    @Test fun fetchStopsAfterBoundedRetriesWhenFallbackAlsoReturnsLocationless307() {
        val original = URI.create("https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=20230701&stockNo=0050&response=json")
        val requests = mutableListOf<URI>()
        val error = assertThrows(BacktestException::class.java) {
            client.fetch(original) { request -> requests += request.uri(); response(request, 307) }
        }
        assertEquals("UPSTREAM_DATA_UNAVAILABLE", error.code)
        assertEquals(6, requests.size)
        assertTrue(requests.take(3).all { it == original })
        assertTrue(requests.drop(3).all { it == client.missingLocationFallback(original) })
    }
    @Test fun fetchDoesNotFallbackForNon307Responses() {
        val original = URI.create("https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=20230701&stockNo=0050&response=json")
        val requests = mutableListOf<URI>()
        val result = client.fetch(original) { request -> requests += request.uri(); response(request, 503) }
        assertEquals(503, result.statusCode())
        assertEquals(listOf(original), requests)
    }

    @Test fun fetchRejectsExternalRedirectWithStableSanitizedUpstreamError() {
        val original = URI.create("https://www.twse.com.tw/exchangeReport/STOCK_DAY?date=20230701&stockNo=0050&response=json")
        val error = assertThrows(BacktestException::class.java) {
            client.fetch(original) { request -> responseWithLocation(request, 302, "https://attacker.invalid/secret?token=private") }
        }
        assertEquals("UPSTREAM_DATA_UNAVAILABLE", error.code)
        assertEquals(502, error.status)
        assertFalse(error.message.orEmpty().contains("attacker"))
        assertFalse(error.message.orEmpty().contains("private"))
    }

    @Test fun protocolAndTransportFailuresBecomeSanitized502Errors() {
        val protocolError = assertThrows(BacktestException::class.java) {
            client.loadMonth("2330", month) { request -> response(request, 200, "<html>secret-token request-id-123</html>") }
        }
        assertEquals("UPSTREAM_DATA_UNAVAILABLE", protocolError.code)
        assertEquals(502, protocolError.status)
        assertFalse(protocolError.message.orEmpty().contains("secret-token"))
        assertFalse(protocolError.message.orEmpty().contains("request-id-123"))

        val transportError = assertThrows(BacktestException::class.java) {
            client.loadMonth("2330", month) { throw java.io.IOException("private host credential") }
        }
        assertEquals("UPSTREAM_DATA_UNAVAILABLE", transportError.code)
        assertEquals(502, transportError.status)
        assertFalse(transportError.message.orEmpty().contains("private host credential"))
    }

    private fun responseWithLocation(request: HttpRequest, status: Int, location: String): HttpResponse<String> = object : HttpResponse<String> {
        override fun statusCode() = status
        override fun request() = request
        override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()
        override fun headers() = HttpHeaders.of(mapOf("location" to listOf(location), "x-request-id" to listOf("private-id"), "content-type" to listOf("text/html"))) { _, _ -> true }
        override fun body() = "private upstream html"
        override fun sslSession(): Optional<SSLSession> = Optional.empty()
        override fun uri() = request.uri()
        override fun version() = HttpClient.Version.HTTP_1_1
    }
}
