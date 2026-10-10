package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.api.BacktestException

@Component
@ConditionalOnProperty(name = ["app.market-data.history-source"], havingValue = "finmind", matchIfMissing = true)
class FinMindHistoricalMarketDataSource(
    private val objectMapper: JsonMapper,
    @Value("\${app.market-data.finmind-url:https://api.finmindtrade.com/api/v4/data}") private val endpoint: String,
    @Value("\${app.market-data.finmind-token:}") private val token: String,
) : HistoricalMarketDataSource {
    override val metadata = METADATA
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override fun fetchHistory(symbol: String, from: LocalDate, to: LocalDate): HistoricalMarketDataImport {
        if (!symbol.matches(Regex("[0-9]{4,6}[A-Z0-9]{0,2}")) || from.isAfter(to) || from.isBefore(MIN_DATE) || to.isAfter(LocalDate.now(Clock.systemUTC()))) {
            throw upstream("FinMind 歷史行情請求的標的或日期範圍無效。")
        }
        val query = listOf("dataset" to "TaiwanStockPrice", "data_id" to symbol, "start_date" to from.toString(), "end_date" to to.toString())
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val request = HttpRequest.newBuilder(URI.create("$endpoint?$query"))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json")
            .header("User-Agent", "TWSEStrategyLab/1.0 (historical market data import)")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .GET().build()
        val response = try { client.send(request, HttpResponse.BodyHandlers.ofInputStream()) }
        catch (error: InterruptedException) { Thread.currentThread().interrupt(); throw upstream("FinMind 歷史行情請求已中斷。") }
        catch (_: Exception) { throw upstream("目前無法連線至 FinMind 歷史行情來源。") }
        val payload = response.body().use { body ->
            if (response.statusCode() !in 200..299) throw upstream("FinMind 歷史行情來源回應 HTTP ${response.statusCode()}。")
            val bytes = try { body.readNBytes(MAX_BODY_BYTES + 1) } catch (_: Exception) { throw upstream("無法讀取 FinMind 歷史行情回應。") }
            if (bytes.size > MAX_BODY_BYTES) throw upstream("FinMind 歷史行情回應超過資料量限制。")
            String(bytes, StandardCharsets.UTF_8)
        }
        val root = try { objectMapper.readTree(payload) } catch (_: Exception) { throw upstream("FinMind 回應不是有效 JSON。") }
        if (root.path("status").asInt(-1) != 200 || !root.path("data").isArray) throw upstream("FinMind 未回傳可用的台股日行情資料。")
        val rows = root.path("data")
        if (rows.size() > MAX_ROWS) throw upstream("FinMind 回傳筆數超過單次匯入限制。")
        val bars = ArrayList<SourceMarketBar>(rows.size())
        val seen = HashSet<LocalDate>()
        var rejected = 0
        rows.forEach { row ->
            val rowSymbol = row.path("stock_id").asText()
            val date = try { LocalDate.parse(row.path("date").asText()) } catch (_: Exception) { throw upstream("FinMind 資料含有無效交易日期。") }
            if (rowSymbol != symbol || date !in from..to || !seen.add(date)) throw upstream("FinMind 資料含有不符請求的標的、日期或重複列。")
            try {
                val open = decimal(row, "open")
                val high = decimal(row, "max")
                val low = decimal(row, "min")
                val close = decimal(row, "close")
                val volume = row.path("Trading_Volume").asLong(-1)
                // FinMind may include zero-price rows when there is no valid trade; never make a fake candle.
                if (open.signum() == 0 && high.signum() == 0 && low.signum() == 0 && close.signum() == 0) {
                    rejected++
                    return@forEach
                }
                if (open.signum() <= 0 || low.signum() <= 0 || high < low || high < open || high < close || low > open || low > close || volume < 0) {
                    throw upstream("FinMind 資料含有不合理 OHLCV。")
                }
                bars += SourceMarketBar(symbol, symbol, date, open, high, low, close, volume)
            } catch (error: BacktestException) { throw error }
            catch (_: Exception) { throw upstream("FinMind 資料含有無效行情數值。") }
        }
        return HistoricalMarketDataImport(METADATA, symbol, from, to, Clock.systemUTC().instant(), bars.sortedBy(SourceMarketBar::date), rejected)
    }

    private fun decimal(row: JsonNode, field: String): BigDecimal = BigDecimal(row.path(field).asText())
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
    private fun upstream(message: String) = BacktestException("UPSTREAM_DATA_UNAVAILABLE", message, 503)

    companion object {
        private const val MAX_BODY_BYTES = 16 * 1024 * 1024
        private const val MAX_ROWS = 10_000
        private val MIN_DATE = LocalDate.of(1994, 10, 1)
        val METADATA = MarketDataSourceMetadata(
            sourceId = "finmind-taiwan-stock-price",
            provider = "FinMind",
            datasetTitle = "TaiwanStockPrice",
            datasetUrl = "https://finmind.github.io/tutor/TaiwanMarket/Technical/",
            resourceUrl = "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockPrice",
            license = "FinMind API access; underlying data redistribution/display rights unverified",
            licenseUrl = "https://finmind.github.io/Disclaimer/",
            attribution = "行情 API 提供者：FinMind；原始行情資料之公開展示與再散布權利尚待確認。",
            licensingStatus = "UNVERIFIED",
        )
    }
}
