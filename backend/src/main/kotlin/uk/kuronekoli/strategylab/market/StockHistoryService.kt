package uk.kuronekoli.strategylab.market

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.regex.Pattern
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.backtest.BacktestValidation

@Service
class StockHistoryService @Autowired constructor(private val marketData: HistoricalMarketDataProvider, @Value("${'$'}{app.market-history.enabled:false}") private val enabled: Boolean) {
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Taipei"))
    internal constructor(marketData: HistoricalMarketDataProvider, clock: Clock, enabled: Boolean) : this(marketData, enabled) { this.injectedClock = clock }
    private var injectedClock: Clock = clock
    private val timeClock get() = injectedClock
    fun history(symbol: String?, fromText: String, toText: String): StockHistoryResponse {
        if (!enabled) throw BacktestException("MARKET_HISTORY_DISABLED", "市場資料圖表目前未在此環境啟用；行情使用權確認前不對外提供。", 503)
        if (symbol == null || !SYMBOL.matcher(symbol).matches()) throw BacktestException.input("請提供 4 至 6 位數字的台股代碼。")
        val from = parseDate(fromText); val to = parseDate(toText); val today = LocalDate.now(timeClock)
        if (from.isBefore(LocalDate.of(2010, 1, 1)) || from.isAfter(to) || to.isAfter(today) || from.plusYears(5).isBefore(to)) throw BacktestException.input("請選擇 2010 年起、截至今日且不超過 5 年的日期範圍。")
        val loaded = marketData.load(symbol, YearMonth.from(from), YearMonth.from(to)); BacktestValidation.bars(loaded)
        val bars = loaded.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        if (bars.isEmpty()) throw BacktestException("NO_MARKET_DATA", "$symbol 在此日期範圍沒有可用收盤資料。", 404)
        val fetchedAt = java.time.OffsetDateTime.now(timeClock).toString()
        val completeOhlcv = bars.all { it.hasOhlcv }
        val limitations = mutableListOf("TWSE STOCK_DAY 是按月提供的歷史日資料，不是即時報價；fetchedAt 是本服務擷取時間，observedTo 是最後一筆資料日期。", "行情授權、歷史完整性與衍生圖表展示權尚未確認；公開發布狀態 BLOCKED。", "資料缺漏與停牌原因未知；價格為未調整資料，未處理股利與公司行動。")
        if (!completeOhlcv) limitations += "此來源回應未提供完整 OHLCV；部分欄位為 null，不可視為 K 線或零成交量。"
        return StockHistoryResponse(symbol, from.toString(), to.toString(), bars.first().date.toString(), bars.last().date.toString(), fetchedAt, marketData.sourceName, "1d", "UNKNOWN", "未調整原始價格", bars.map { StockHistoryResponse.Bar(it.date.toString(), it.close.toPlainString(), it.open?.toPlainString(), it.high?.toPlainString(), it.low?.toPlainString(), it.volume) }, limitations, realtime = false)
    }
    private fun parseDate(value: String): LocalDate = try { LocalDate.parse(value) } catch (_: RuntimeException) { throw BacktestException.input("日期格式不正確；請使用 YYYY-MM-DD。") }
    companion object { private val SYMBOL = Pattern.compile("\\d{4,6}") }
}
