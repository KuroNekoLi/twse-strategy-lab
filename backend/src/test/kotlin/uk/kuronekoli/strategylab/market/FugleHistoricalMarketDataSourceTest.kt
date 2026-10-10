package uk.kuronekoli.strategylab.market

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.api.BacktestException

class FugleHistoricalMarketDataSourceTest {
    @Test
    fun `imports less-than-year Fugle chunks and records confirmed historical display rights`() {
        val requests = CopyOnWriteArrayList<Pair<String, String?>>()
        withSource({ exchange ->
            requests += exchange.requestURI.rawQuery to exchange.requestHeaders.getFirst("X-API-KEY")
            val date = exchange.requestURI.rawQuery.substringAfter("from=").substringBefore('&')
            respond(exchange, """{"symbol":"0050","data":[{"date":"$date","open":100,"high":102,"low":99,"close":101,"volume":123456}]}""")
        }) { endpoint ->
            val imported = source(endpoint, "test-key").fetchHistory(
                "0050", LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-01"),
            )
            assertEquals(2, imported.bars.size)
            assertEquals(listOf("2025-01-01", "2026-01-01"), imported.bars.map { it.date.toString() })
            assertEquals(2, requests.size)
            assertEquals(listOf("test-key", "test-key"), requests.map { it.second })
            assertEquals("CONFIRMED", imported.metadata.licensingStatus)
            assertEquals("fugle-taiwan-stock-historical-candles", imported.metadata.sourceId)
        }
    }

    @Test
    fun `rejects incomplete OHLC response`() {
        withSource({ exchange -> respond(exchange, """{"symbol":"0050","data":[{"date":"2026-10-08","open":100,"high":90,"low":99,"close":101,"volume":1}]}""") }) { endpoint ->
            assertThrows(BacktestException::class.java) {
                source(endpoint, "test-key").fetchHistory("0050", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"))
            }
        }
    }

    @Test
    fun `does not make a request without API key`() {
        assertThrows(BacktestException::class.java) {
            source("http://127.0.0.1:1/candles", "").fetchHistory("0050", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"))
        }
    }

    @Test
    fun `does not send API key to a non-Fugle host`() {
        assertThrows(BacktestException::class.java) {
            source("https://attacker.invalid/candles", "test-key").fetchHistory("0050", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"))
        }
    }

    private fun source(endpoint: String, key: String) = FugleHistoricalMarketDataSource(JsonMapper.builder().build(), endpoint, key)

    private fun withSource(handler: (com.sun.net.httpserver.HttpExchange) -> Unit, block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/candles") { exchange -> handler(exchange) }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}/candles") }
        finally { server.stop(0) }
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
