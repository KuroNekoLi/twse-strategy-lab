package uk.kuronekoli.strategylab.market

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.regex.Pattern
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.backtest.BacktestValidation

@Service
class StockHistoryService @Autowired constructor(
    private val marketData: HistoricalMarketDataProvider,
    @Value("${'$'}{app.market-history.enabled:false}") private val enabled: Boolean,
) {
    private var injectedClock: Clock = Clock.system(ZoneId.of("Asia/Taipei"))

    internal constructor(marketData: HistoricalMarketDataProvider, clock: Clock, enabled: Boolean) : this(marketData, enabled) {
        injectedClock = clock
    }

    fun history(symbol: String?, fromText: String, toText: String, intervalText: String = "1d"): StockHistoryResponse {
        if (!enabled) throw BacktestException("MARKET_HISTORY_DISABLED", "市場資料圖表目前未在此環境啟用；行情使用權確認前不對外提供。", 503)
        if (symbol == null || !SYMBOL.matcher(symbol).matches()) throw BacktestException.input("請提供 4 至 6 位數字的台股代碼。")

        val interval = HistoryInterval.parse(intervalText)
        val from = parseDate(fromText)
        val to = parseDate(toText)
        val today = LocalDate.now(injectedClock.withZone(TAIPEI_ZONE))
        if (from.isBefore(MIN_DATE) || from.isAfter(to) || to.isAfter(today) || from.plusYears(5).isBefore(to)) {
            throw BacktestException.input("請選擇 2010 年起、截至今日且不超過 5 年的日期範圍。")
        }

        val loaded = marketData.load(symbol, YearMonth.from(from), YearMonth.from(to))
        BacktestValidation.bars(loaded)
        // Filter daily observations before bucketing so adjacent periods cannot leak into the request.
        val observations = loaded.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        if (observations.isEmpty()) throw BacktestException("NO_MARKET_DATA", "$symbol 在此日期範圍沒有可用收盤資料。", 404)

        val fetchedAt = java.time.OffsetDateTime.now(injectedClock.withZone(TAIPEI_ZONE)).toString()
        val completeOhlcv = observations.all(DailyBar::hasOhlcv)
        val limitations = mutableListOf(
            "TWSE STOCK_DAY 是按月提供的歷史日資料，不是即時報價；fetchedAt 是本服務擷取時間，observedTo 是最後一筆資料日期。",
            "行情授權、歷史完整性與衍生圖表展示權尚未確認；公開發布狀態 BLOCKED。",
            "資料缺漏與停牌原因未知；價格為未調整資料，未處理股利與公司行動。",
            "coverageStatus=UNKNOWN；觀測筆數不代表交易日資料完整。",
        )
        if (!completeOhlcv) limitations += "此來源回應未提供完整 OHLCV；部分欄位為 null，不可視為 K 線或零成交量。"

        val bars = aggregate(observations, interval, from, to, today)
        return StockHistoryResponse(
            symbol = symbol,
            from = from.toString(),
            to = to.toString(),
            observedFrom = observations.first().date.toString(),
            observedTo = observations.last().date.toString(),
            fetchedAt = fetchedAt,
            source = marketData.sourceName,
            interval = interval.externalValue,
            licensingStatus = "UNKNOWN",
            adjustmentPolicy = "未調整原始價格",
            bars = bars,
            limitations = limitations,
            realtime = false,
        )
    }

    private fun aggregate(
        observations: List<DailyBar>,
        interval: HistoryInterval,
        requestedFrom: LocalDate,
        requestedTo: LocalDate,
        today: LocalDate,
    ): List<StockHistoryResponse.Bar> = observations
        .groupBy { interval.periodStart(it.date) }
        .toSortedMap()
        .map { (periodStart, periodBars) ->
            val periodEnd = interval.periodEnd(periodStart)
            val first = periodBars.first()
            val last = periodBars.last()
            val fullOhlcv = periodBars.all(DailyBar::hasOhlcv)
            StockHistoryResponse.Bar(
                date = if (interval == HistoryInterval.DAILY) first.date.toString() else periodStart.toString(),
                close = last.close.toPlainString(),
                open = if (fullOhlcv) first.open!!.toPlainString() else null,
                high = if (fullOhlcv) periodBars.maxOf { it.high!! }.toPlainString() else null,
                low = if (fullOhlcv) periodBars.minOf { it.low!! }.toPlainString() else null,
                volume = if (fullOhlcv) periodBars.fold(0L) { total, bar -> Math.addExact(total, bar.volume!!) } else null,
                periodStart = periodStart.toString(),
                periodEnd = periodEnd.toString(),
                observedFrom = first.date.toString(),
                observedTo = last.date.toString(),
                periodWindowStatus = when {
                    periodStart.isBefore(requestedFrom) -> "CLIPPED_BY_REQUEST"
                    !today.isBefore(periodStart) && !today.isAfter(periodEnd) && requestedTo == today -> "IN_PROGRESS"
                    periodEnd.isAfter(requestedTo) -> "CLIPPED_BY_REQUEST"
                    else -> "ELAPSED"
                },
                coverageStatus = "UNKNOWN",
            )
        }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (_: RuntimeException) {
        throw BacktestException.input("日期格式不正確；請使用 YYYY-MM-DD。")
    }

    private enum class HistoryInterval(val externalValue: String) {
        DAILY("1d"), WEEKLY("1w"), MONTHLY("1mo");

        fun periodStart(date: LocalDate): LocalDate = when (this) {
            DAILY -> date
            WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            MONTHLY -> date.withDayOfMonth(1)
        }

        fun periodEnd(start: LocalDate): LocalDate = when (this) {
            DAILY -> start
            WEEKLY -> start.plusDays(6)
            MONTHLY -> YearMonth.from(start).atEndOfMonth()
        }

        companion object {
            fun parse(value: String): HistoryInterval = entries.firstOrNull { it.externalValue == value }
                ?: throw BacktestException.input("interval 僅支援 1d、1w 或 1mo。")
        }
    }

    companion object {
        private val SYMBOL = Pattern.compile("\\d{4,6}")
        private val MIN_DATE = LocalDate.of(2010, 1, 1)
        private val TAIPEI_ZONE = ZoneId.of("Asia/Taipei")
    }
}
