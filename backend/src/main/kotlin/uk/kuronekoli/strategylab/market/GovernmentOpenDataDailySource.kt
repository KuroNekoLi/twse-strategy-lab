package uk.kuronekoli.strategylab.market

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.kuronekoli.strategylab.api.BacktestException

@Component
class GovernmentOpenDataDailySource(
    @Value("\${app.market-data.government-open-data-url:https://www.twse.com.tw/exchangeReport/STOCK_DAY_ALL?response=open_data}") private val endpoint: String,
) : DailyMarketDataSource {
    private val clock: Clock = Clock.systemUTC()
    private val uri = URI.create(endpoint)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun fetchLatestSnapshot(): MarketDataSnapshot {
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(25))
            .header("Accept", "text/csv")
            .header("User-Agent", "TWSEStrategyLab/1.0 (open government daily market data)")
            .GET()
            .build()
        val response = fetchCsvWithRetry(request)
        val fetchedAt = clock.instant()
        val parsed = parseCsvWithQuality(response)
        val bars = parsed.bars
        val dates = bars.map { it.date }.distinct()
        if (bars.isEmpty() || dates.size != 1) throw upstream("政府開放資料快照日期不一致或沒有有效日線。")
        return MarketDataSnapshot(METADATA.copy(attribution = attributionFor(dates.single().year)), dates.single(), fetchedAt, bars, parsed.rowsRejected)
    }

    internal fun parseCsv(csv: String): List<SourceMarketBar> = parseCsvWithQuality(csv).bars

    internal fun parseCsvWithQuality(csv: String): ParsedCsvSnapshot {
        if (csv.isBlank() || csv.toByteArray(StandardCharsets.UTF_8).size > MAX_BODY_BYTES) throw upstream("政府開放資料 CSV 為空或超過允許大小。")
        val rows = parseRecords(csv.removePrefix("\uFEFF"))
        if (rows.size < 2 || rows.size > MAX_ROWS + 1 || rows.first() != HEADERS) throw upstream("政府開放資料 CSV 欄位格式不符。")
        val bars = ArrayList<SourceMarketBar>(rows.size - 1)
        val seen = HashSet<Pair<String, LocalDate>>()
        var rejected = 0
        rows.drop(1).forEach { row ->
            if (row.size != HEADERS.size) throw upstream("政府開放資料 CSV 含有欄位數不符的資料列。")
            val symbol = row[1].trim()
            val name = row[2].trim()
            // TWSE includes special security suffixes such as 2887Z1 in addition to ETF suffixes like 00679B.
            if (!symbol.matches(Regex("[0-9]{4,6}[A-Z0-9]{0,2}")) || name.isBlank() || name.length > 200) throw upstream("政府開放資料 CSV 含有無效標的識別。")
            val date = parseRocDate(row[0].trim())
            // Some listed securities have no published OHLC on a snapshot (usually no transactions).
            // Keep schema/identity checks strict, but do not invent a zero-price candle for them.
            if ((5..8).any { row[it].isBlank() }) {
                rejected++
                return@forEach
            }
            val volume = try { parseNumber(row[3]).longValueExact() } catch (_: ArithmeticException) { throw upstream("政府開放資料 CSV 含有無效成交股數。") }
            val amount = try { parseNumber(row[4]).longValueExact() } catch (_: ArithmeticException) { throw upstream("政府開放資料 CSV 含有無效成交金額。") }
            val open = parseNumber(row[5])
            val high = parseNumber(row[6])
            val low = parseNumber(row[7])
            val close = parseNumber(row[8])
            parseNumber(row[9])
            val trades = try { parseNumber(row[10]).longValueExact() } catch (_: ArithmeticException) { throw upstream("政府開放資料 CSV 含有無效成交筆數。") }
            if (volume < 0 || amount < 0 || trades < 0 || open.signum() <= 0 || high < low || high < open || high < close || low > open || low > close) {
                throw upstream("政府開放資料 CSV 含有不合理 OHLCV。")
            }
            if (!seen.add(symbol to date)) throw upstream("政府開放資料 CSV 含有重複標的與日期。")
            bars += SourceMarketBar(symbol, name, date, open, high, low, close, volume)
        }
        return ParsedCsvSnapshot(bars, rejected)
    }

    private fun parseRecords(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { row += field.toString().trim(); field.setLength(0) }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    row += field.toString().trim(); field.setLength(0)
                    if (row.any(String::isNotEmpty)) rows += row
                    row = mutableListOf()
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> field.append(ch)
            }
            i++
        }
        if (quoted) throw upstream("政府開放資料 CSV 引號結構錯誤。")
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString().trim()
            if (row.any(String::isNotEmpty)) rows += row
        }
        return rows
    }

    private fun parseRocDate(value: String): LocalDate = try {
        require(value.matches(Regex("[0-9]{7}")))
        LocalDate.parse("${value.substring(0, 3).toInt() + 1911}${value.substring(3)}", DateTimeFormatter.BASIC_ISO_DATE)
    } catch (_: RuntimeException) { throw upstream("政府開放資料 CSV 含有無效交易日期。") }

    private fun parseNumber(value: String): java.math.BigDecimal = try {
        java.math.BigDecimal(value.trim().replace(",", "")).also { require(it.scale() <= 8) }
    } catch (_: RuntimeException) { throw upstream("政府開放資料 CSV 含有無效行情數值。") }

    private fun upstream(message: String) = BacktestException("UPSTREAM_DATA_UNAVAILABLE", message, 503)

    private fun fetchCsvWithRetry(request: HttpRequest): String {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val http = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
                http.body().use { body ->
                    if (http.statusCode() !in 200..299) {
                        if (http.statusCode() == 429 || http.statusCode() >= 500) throw RetryableStatus(http.statusCode())
                        throw upstream("政府開放資料來源回應 HTTP ${http.statusCode()}。")
                    }
                    val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "market-csv-reader").apply { isDaemon = true } }
                    try {
                        val bytes = reader.submit<ByteArray> { body.readNBytes(MAX_BODY_BYTES + 1) }.get(25, TimeUnit.SECONDS)
                        if (bytes.size > MAX_BODY_BYTES) throw upstream("政府開放資料回應超過允許大小。")
                        return String(bytes, StandardCharsets.UTF_8)
                    } finally {
                        reader.shutdownNow()
                    }
                }
            } catch (error: BacktestException) {
                throw error
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw upstream("政府開放資料請求已中斷。")
            } catch (error: RetryableStatus) {
                if (attempt == MAX_ATTEMPTS - 1) throw upstream("政府開放資料來源暫時回應 HTTP ${error.status}。")
            } catch (error: Exception) {
                if (attempt == MAX_ATTEMPTS - 1) throw upstream("目前無法取得政府開放資料日行情。")
            }
            try { Thread.sleep(300L * (attempt + 1)) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw upstream("政府開放資料請求已中斷。")
            }
        }
        throw upstream("目前無法取得政府開放資料日行情。")
    }

    private class RetryableStatus(val status: Int) : RuntimeException()

    companion object {
        private const val MAX_BODY_BYTES = 8 * 1024 * 1024
        private const val MAX_ROWS = 20_000
        private const val MAX_ATTEMPTS = 3
        private val HEADERS = listOf("日期", "證券代號", "證券名稱", "成交股數", "成交金額", "開盤價", "最高價", "最低價", "收盤價", "漲跌價差", "成交筆數")
        val METADATA = MarketDataSourceMetadata(
            sourceId = "twse-listed-daily-open-data",
            provider = "臺灣證券交易所",
            datasetTitle = "上市個股日成交資訊",
            datasetUrl = "https://data.gov.tw/dataset/11549",
            resourceUrl = "https://www.twse.com.tw/exchangeReport/STOCK_DAY_ALL?response=open_data",
            license = "政府資料開放授權條款第1版（OGDL v1.0）",
            licenseUrl = "https://data.gov.tw/license",
            attribution = attributionFor(LocalDate.now().year),
        )

        fun attributionFor(year: Int) = "資料提供機關：金融監督管理委員會證券期貨局；原始資料來源：臺灣證券交易所，$year，上市個股日成交資訊；政府資料開放授權條款第1版。"
    }

    internal data class ParsedCsvSnapshot(val bars: List<SourceMarketBar>, val rowsRejected: Int)
}
