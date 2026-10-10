package uk.kuronekoli.strategylab.market

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.api.BacktestException

class FinMindHistoricalMarketDataSourceTest {
    @Test
    fun `parses FinMind TaiwanStockPrice OHLCV and keeps rights unverified`() {
        withSource("""{"status":200,"data":[{"date":"2026-10-08","stock_id":"0050","Trading_Volume":99877876,"open":115.35,"max":115.55,"min":114.90,"close":114.95}]}""") { source ->
            val imported = source.fetchHistory("0050", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"))
            assertEquals(1, imported.bars.size)
            assertEquals("114.95", imported.bars.single().close.toPlainString())
            assertEquals(99_877_876L, imported.bars.single().volume)
            assertEquals("UNVERIFIED", imported.metadata.licensingStatus)
        }
    }

    @Test
    fun `rejects malformed OHLC instead of storing a misleading candle`() {
        withSource("""{"status":200,"data":[{"date":"2026-10-08","stock_id":"0050","Trading_Volume":1,"open":115.35,"max":110,"min":114.90,"close":114.95}]}""") { source ->
            assertThrows(BacktestException::class.java) {
                source.fetchHistory("0050", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-10"))
            }
        }
    }

    private fun withSource(payload: String, block: (FinMindHistoricalMarketDataSource) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/data") { exchange ->
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val endpoint = "http://127.0.0.1:${server.address.port}/data"
        try { block(FinMindHistoricalMarketDataSource(JsonMapper.builder().build(), endpoint, "")) }
        finally { server.stop(0) }
    }
}
