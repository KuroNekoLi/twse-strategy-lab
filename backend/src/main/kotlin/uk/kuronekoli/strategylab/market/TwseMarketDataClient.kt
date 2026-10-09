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
            if (!groupStart.plusMonths(3).isAfter(last)) try { Thread.sleep(120) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IllegalStateException("行情請求已中斷。", e) }
            groupStart = groupStart.plusMonths(3)
        }
        BacktestValidation.bars(bars)
        return bars.toList()
    }

    private fun loadMonth(symbol: String, month: YearMonth): List<DailyBar> {
        val query = "?date=${month.atDay(1).toString().replace("-", "")}&stockNo=${URLEncoder.encode(symbol, StandardCharsets.UTF_8)}&response=json"
        // URI.resolve("?query") drops the endpoint's last path segment; append the query to preserve STOCK_DAY.
        val uri = URI.create(endpoint.toString() + query)
        try {
            val response = fetch(uri)
            if (response.statusCode() !in 200..299) throw BacktestException.upstream("$symbol／$month：證交所行情服务回應 ${response.statusCode()}。")
            val root = try { mapper.readTree(response.body()) } catch (e: Exception) {
                log.warn("TWSE response was not valid JSON: symbol={}, month={}, status={}, contentType={}, server={}, upstreamRequestId={}, bodyLength={}, firstCharacter={}", symbol, month, response.statusCode(), response.headers().firstValue("content-type").orElse("missing"), response.headers().firstValue("server").orElse("missing"), response.headers().firstValue("x-request-id").orElse("missing"), response.body().length, if (response.body().isEmpty()) "empty" else response.body().substring(0, 1), e)
                throw e
            }
            return parseMonthResponse(symbol, month, root)
        } catch (e: BacktestException) { throw e }
        catch (e: IllegalStateException) {
            log.warn("TWSE data request failed: symbol={}, month={}, exceptionType={}, reason={}", symbol, month, e.javaClass.name, e.message); throw e
        } catch (e: Exception) {
            val root = rootCause(e)
            log.warn("TWSE data request failed: symbol={}, month={}, exceptionType={}, rootCauseType={}, rootCauseReason={}", symbol, month, e.javaClass.name, root.javaClass.name, root.message, e)
            if (e is InterruptedException) Thread.currentThread().interrupt()
            throw BacktestException.upstream("$symbol／$month：無法讀取證交所行情，請稍後再試。")
        }
    }

    /** Strict package-visible parser permits offline synthetic provider-shape tests. */
    internal fun parseMonthResponse(symbol: String, month: YearMonth, root: JsonNode?): List<DailyBar> {
        val context = "$symbol／$month："
        if (root == null || !root.isObject || !root.path("stat").isTextual || root.path("stat").asText() != "OK") throw BacktestException("UPSTREAM_MONTH_STATUS_UNKNOWN", "${context}行情狀態非 OK；上市、停牌及缺漏原因未知，已停止計算。", 502)
        val data = root.path("data")
        if (!data.isArray || data.isEmpty) throw BacktestException("UPSTREAM_EMPTY_MONTH", "${context}月份資料為空或格式不正確，已停止計算。", 502)
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

    private fun fetch(start: URI): HttpResponse<String> {
        var current = start
        repeat(4) {
            var response: HttpResponse<String>? = null
            for (attempt in 0..2) {
                val request = HttpRequest.newBuilder(current).timeout(Duration.ofSeconds(20)).header("Accept", "application/json, text/plain, */*").header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8").header("Referer", "https://www.twse.com.tw/").header("User-Agent", "Mozilla/5.0 (compatible; TWSEStrategyLab/1.0)").GET().build()
                response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                val noLocation = response.statusCode() in 300..399 && response.headers().firstValue("location").isEmpty
                if (!noLocation || attempt == 2) break
                Thread.sleep(250L * (attempt + 1))
            }
            val result = response!!
            if (result.statusCode() !in 300..399) return result
            val locationHeader = result.headers().firstValue("location")
            if (locationHeader.isEmpty) throw IllegalStateException("證交所回應轉址但未提供目的位址（status=${result.statusCode()}, server=${result.headers().firstValue("server").orElse("missing")}, requestId=${result.headers().firstValue("x-request-id").orElse("missing")}, contentType=${result.headers().firstValue("content-type").orElse("missing")}, bodyLength=${result.body().length}）。")
            val next = current.resolve(locationHeader.get())
            val host = next.host?.lowercase() ?: ""
            if (!next.scheme.equals("https", true) || !(host == "twse.com.tw" || host.endsWith(".twse.com.tw"))) throw IllegalStateException("證交所行情服務導向非證交所網址，已停止請求。")
            current = next
        }
        throw IllegalStateException("證交所行情服務轉址次數過多，請稍後再試。")
    }
}
