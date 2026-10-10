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
@ConditionalOnProperty(name = ["app.market-data.history-source"], havingValue = "fugle")
class FugleHistoricalMarketDataSource(
    private val objectMapper: JsonMapper,
    @Value("\${app.market-data.fugle-historical-url:https://api.fugle.tw/marketdata/v1.0/stock/historical/candles}")
    private val endpoint: String,
    @Value("\${app.market-data.fugle-api-key:}") private val apiKey: String,
) : HistoricalMarketDataSource {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override fun fetchHistory(symbol: String, from: LocalDate, to: LocalDate): HistoricalMarketDataImport {
        if (!symbol.matches(Regex("[0-9]{4,6}[A-Z0-9]{0,2}")) || from.isAfter(to) || from.isBefore(MIN_DATE) || to.isAfter(LocalDate.now(Clock.systemUTC()))) {
            throw upstream("Fugle 歷史行情請求的標的或日期範圍無效。")
        }
        if (apiKey.isBlank()) throw upstream("尚未設定 Fugle 歷史行情 API 金鑰。")

        val bars = ArrayList<SourceMarketBar>()
        val seen = HashSet<LocalDate>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            // Fugle requires each query span to be strictly shorter than one year.
            val chunkEnd = minOf(cursor.plusYears(1).minusDays(1), to)
            val rows = fetchChunk(symbol, cursor, chunkEnd)
            rows.forEach { row ->
                val bar = parseBar(row, symbol, cursor, chunkEnd)
                if (!seen.add(bar.date)) throw upstream("Fugle 歷史行情回應含重複交易日期。")
                bars += bar
            }
            cursor = chunkEnd.plusDays(1)
        }
        if (bars.size > MAX_ROWS) throw upstream("Fugle 回傳筆數超過單次匯入限制。")
        return HistoricalMarketDataImport(
            metadata = METADATA,
            symbol = symbol,
            from = from,
            to = to,
            fetchedAt = Clock.systemUTC().instant(),
            bars = bars.sortedBy(SourceMarketBar::date),
        )
    }

    private fun fetchChunk(symbol: String, from: LocalDate, to: LocalDate): List<JsonNode> {
        validateEndpoint()
        val encodedSymbol = URLEncoder.encode(symbol, StandardCharsets.UTF_8)
        val query = "from=$from&to=$to&timeframe=D&fields=open,high,low,close,volume&sort=asc"
        val request = HttpRequest.newBuilder(URI.create("${endpoint.trimEnd('/')}/$encodedSymbol?$query"))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("X-API-KEY", apiKey)
            .GET().build()
        val response = try { client.send(request, HttpResponse.BodyHandlers.ofInputStream()) }
        catch (error: InterruptedException) { Thread.currentThread().interrupt(); throw upstream("Fugle 歷史行情請求已中斷。") }
        catch (_: Exception) { throw upstream("目前無法連線至 Fugle 歷史行情來源。") }
        val payload = response.body().use { body ->
            if (response.statusCode() !in 200..299) throw upstream("Fugle 歷史行情來源回應 HTTP ${response.statusCode()}。")
            val bytes = try { body.readNBytes(MAX_BODY_BYTES + 1) } catch (_: Exception) { throw upstream("無法讀取 Fugle 歷史行情回應。") }
            if (bytes.size > MAX_BODY_BYTES) throw upstream("Fugle 歷史行情回應超過資料量限制。")
            String(bytes, StandardCharsets.UTF_8)
        }
        val root = try { objectMapper.readTree(payload) } catch (_: Exception) { throw upstream("Fugle 回應不是有效 JSON。") }
        if (root.path("symbol").isTextual && root.path("symbol").asText() != symbol) throw upstream("Fugle 回應的標的與請求不符。")
        val rows = root.path("data")
        if (!rows.isArray || rows.size() > MAX_ROWS) throw upstream("Fugle 未回傳可用的台股日行情資料。")
        return rows.toList()
    }

    private fun validateEndpoint() {
        val uri = try { URI.create(endpoint) } catch (_: Exception) { throw upstream("Fugle 歷史行情端點設定無效。") }
        val host = uri.host?.lowercase()
        val isFugle = uri.scheme == "https" && host == "api.fugle.tw" && uri.rawUserInfo == null && uri.port in setOf(-1, 443)
        val isLocalTest = uri.scheme == "http" && host in setOf("127.0.0.1", "localhost", "::1")
        if (!isFugle && !isLocalTest) throw upstream("Fugle 歷史行情端點必須使用官方 HTTPS 主機。")
    }

    private fun parseBar(row: JsonNode, symbol: String, from: LocalDate, to: LocalDate): SourceMarketBar {
        try {
            val date = LocalDate.parse(row.path("date").asText())
            val open = decimal(row, "open")
            val high = decimal(row, "high")
            val low = decimal(row, "low")
            val close = decimal(row, "close")
            val volume = row.path("volume").asLong(-1)
            if (date !in from..to || open.signum() <= 0 || low.signum() <= 0 || high < low || high < open || high < close || low > open || low > close || volume < 0) {
                throw upstream("Fugle 歷史行情資料含有不合理的日期或 OHLCV。")
            }
            return SourceMarketBar(symbol, symbol, date, open, high, low, close, volume)
        } catch (error: BacktestException) { throw error }
        catch (_: Exception) { throw upstream("Fugle 歷史行情資料含有無效日期或數值。") }
    }

    private fun decimal(row: JsonNode, field: String) = BigDecimal(row.path(field).asText())
    private fun upstream(message: String) = BacktestException("UPSTREAM_DATA_UNAVAILABLE", message, 503)

    companion object {
        private const val MAX_BODY_BYTES = 16 * 1024 * 1024
        private const val MAX_ROWS = 10_000
        private val MIN_DATE = LocalDate.of(2010, 1, 1)
        val METADATA = MarketDataSourceMetadata(
            sourceId = "fugle-taiwan-stock-historical-candles",
            provider = "Fugle / 時報資訊",
            datasetTitle = "台股歷史行情（日 K）",
            datasetUrl = "https://developer.fugle.tw/docs/data/http-api/historical/candles/",
            resourceUrl = "https://api.fugle.tw/marketdata/v1.0/stock/historical/candles",
            license = "Fugle API access; public storage and display rights require provider confirmation",
            licenseUrl = "https://developer.fugle.tw/docs/data/intro/",
            attribution = "資料來源：Fugle／時報資訊；公開展示權待確認。",
            licensingStatus = "UNVERIFIED",
        )
    }
}
