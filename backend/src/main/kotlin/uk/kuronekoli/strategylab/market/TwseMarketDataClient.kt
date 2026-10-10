package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.YearMonth
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.backtest.BacktestValidation

@Component
class TwseMarketDataClient(private val mapper: JsonMapper, @Value("\${app.twse.base-url}") endpoint: String) : HistoricalMarketDataProvider {
    private val log = LoggerFactory.getLogger(TwseMarketDataClient::class.java)
    private val endpoint = URI.create(endpoint)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()

    override val sourceName: String = "TWSE STOCK_DAY（月歷史端點）"

    override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> {
        val bars = mutableListOf<DailyBar>()
        var groupStart = first
        while (!groupStart.isAfter(last)) {
            val group = (0..2).map { groupStart.plusMonths(it.toLong()) }.filterNot { it.isAfter(last) }
            bars += group.parallelStream().flatMap { loadMonth(symbol, it).stream() }.toList()
            if (!groupStart.plusMonths(3).isAfter(last)) try { Thread.sleep(120) } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("TWSE request batch interrupted: symbol={}, firstMonth={}, lastMonth={}", symbol, first, last)
                throw BacktestException.upstream("歷史行情請求已中斷，請稍後再試。")
            }
            groupStart = groupStart.plusMonths(3)
        }
        if (bars.isNotEmpty()) BacktestValidation.bars(bars)
        return bars.toList()
    }

    private fun loadMonth(symbol: String, month: YearMonth): List<DailyBar> {
        return loadMonth(symbol, month) { request -> http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)) }
    }

    internal fun loadMonth(symbol: String, month: YearMonth, send: (HttpRequest) -> HttpResponse<String>): List<DailyBar> {
        val query = "?date=${month.atDay(1).toString().replace("-", "")}&stockNo=${URLEncoder.encode(symbol, StandardCharsets.UTF_8)}&response=json"
        // URI.resolve("?query") drops the endpoint's last path segment; append the query to preserve STOCK_DAY.
        val uri = URI.create(endpoint.toString() + query)
        try {
            val response = fetch(uri, send)
            if (response.statusCode() !in 200..299) throw BacktestException.upstream("無法讀取歷史行情資料，請稍後再試。")
            val root = try { mapper.readTree(response.body()) } catch (e: Exception) {
                log.warn("TWSE response was not valid JSON: symbol={}, month={}, status={}, bodyLength={}", symbol, month, response.statusCode(), response.body().length)
                throw BacktestException.upstream("無法讀取歷史行情資料，請稍後再試。")
            }
            return parseMonthResponse(symbol, month, root)
        } catch (e: BacktestException) { throw e }
        catch (e: IllegalStateException) {
            log.warn("TWSE data request failed: symbol={}, month={}, exceptionType={}", symbol, month, e.javaClass.name)
            throw BacktestException.upstream("無法讀取歷史行情資料，請稍後再試。")
        } catch (e: Exception) {
            val root = rootCause(e)
            log.warn("TWSE data request failed: symbol={}, month={}, exceptionType={}, rootCauseType={}", symbol, month, e.javaClass.name, root.javaClass.name)
            if (e is InterruptedException) Thread.currentThread().interrupt()
            throw BacktestException.upstream("無法讀取歷史行情資料，請稍後再試。")
        }
    }

    /** Strict package-visible parser permits offline synthetic provider-shape tests. */
    internal fun parseMonthResponse(symbol: String, month: YearMonth, root: JsonNode?): List<DailyBar> {
        val context = "$symbol／$month："
        if (root == null || !root.isObject || !root.path("stat").isTextual || root.path("stat").asText() != "OK") throw BacktestException.upstream("${context}無法取得有效行情資料；上市、停牌與缺漏原因未知。")
        val data = root.path("data")
        if (!data.isArray) throw BacktestException.upstream("${context}無法取得有效行情資料；上市、停牌與缺漏原因未知。")
        if (data.isEmpty) throw BacktestException.upstream("${context}無法取得有效行情資料；上市、停牌與缺漏原因未知。")
        if (data.size() > 31) throw BacktestException.data("${context}月份行情筆數超出上限。")
        val rows = mutableListOf<DailyBar>()
        for (row in data) try {
            if (!row.isArray || row.size() < 9 || (0..8).any { !row[it].isTextual }) throw IllegalArgumentException("row shape")
            val dateText = row[0].asText().trim()
            if (!dateText.matches(Regex("\\d{2,4}/\\d{1,2}/\\d{1,2}"))) throw IllegalArgumentException("date shape")
            val parts = dateText.split("/"); var year = parts[0].toInt(); if (year < 1911) year += 1911
            val date = LocalDate.of(year, parts[1].toInt(), parts[2].toInt())
            if (YearMonth.from(date) != month) throw IllegalArgumentException("wrong month")
            val volume = parseInteger(row[1].asText(), "volume")
            val open = parsePrice(row[3].asText())
            val high = parsePrice(row[4].asText())
            val low = parsePrice(row[5].asText())
            val close = parsePrice(row[6].asText())
            rows += DailyBar(date, close, open, high, low, volume)
        } catch (_: RuntimeException) { throw BacktestException.data("${context}行情列含無效日期、OHLC 價格或成交量；不跳過錯誤列，已停止計算。") }
        try { BacktestValidation.bars(rows) } catch (e: BacktestException) { throw BacktestException.data(context + e.message) }
        return rows.toList()
    }

    private fun parsePrice(raw: String): BigDecimal {
        val price = raw.trim()
        if (!price.matches(Regex("(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d+)?"))) throw IllegalArgumentException("price shape")
        return BigDecimal(price.replace(",", "")).also { require(it.signum() > 0) }
    }

    private fun parseInteger(raw: String, field: String): Long {
        val value = raw.trim()
        if (!value.matches(Regex("(?:\\d+|\\d{1,3}(?:,\\d{3})+)"))) throw IllegalArgumentException("$field shape")
        return value.replace(",", "").toLong()
    }

    private fun rootCause(error: Throwable): Throwable {
        var root = error
        while (root.cause != null && root.cause !== root) root = root.cause!!
        return root
    }

    private fun fetch(start: URI): HttpResponse<String> = fetch(start) { request -> http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)) }

    internal fun fetch(start: URI, send: (HttpRequest) -> HttpResponse<String>): HttpResponse<String> {
        var current = start
        repeat(4) {
            var response: HttpResponse<String>? = null
            for (attempt in 0..2) {
                val request = HttpRequest.newBuilder(current).timeout(Duration.ofSeconds(20)).header("Accept", "application/json, text/plain, */*").header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8").header("Referer", "https://www.twse.com.tw/").header("User-Agent", "Mozilla/5.0 (compatible; TWSEStrategyLab/1.0)").GET().build()
                response = send(request)
                val noLocation = response.statusCode() in 300..399 && response.headers().firstValue("location").isEmpty
                if (!noLocation || attempt == 2) break
                Thread.sleep(250L * (attempt + 1))
            }
            val result = response!!
            if (result.statusCode() !in 300..399) return result
            val locationHeader = result.headers().firstValue("location")
            if (locationHeader.isEmpty) {
                val fallback = missingLocationFallback(current)
                if (result.statusCode() == 307 && fallback != null && current != fallback) {
                    log.warn("TWSE rwd endpoint returned 307 without Location; retrying equivalent exchangeReport endpoint: symbolQueryPresent={}", !current.rawQuery.isNullOrBlank())
                    current = fallback
                    return@repeat
                }
                throw BacktestException.upstream("無法讀取歷史行情資料，請稍後再試。")
            }
            val next = current.resolve(locationHeader.get())
            val host = next.host?.lowercase() ?: ""
            if (!next.scheme.equals("https", true) || !(host == "twse.com.tw" || host.endsWith(".twse.com.tw"))) throw BacktestException.upstream("無法讀取歷史行情資料，請稍後再試。")
            current = next
        }
        throw BacktestException.upstream("無法讀取歷史行情資料，請稍後再試。")
    }

    internal fun missingLocationFallback(uri: URI): URI? {
        if (uri.scheme != "https" || uri.host?.lowercase() != "www.twse.com.tw" || uri.path != "/rwd/zh/afterTrading/STOCK_DAY") return null
        val query = uri.rawQuery ?: return null
        return URI.create("https://www.twse.com.tw/exchangeReport/STOCK_DAY?$query")
    }
}
