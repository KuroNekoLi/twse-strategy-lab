package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.regex.Pattern
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import uk.kuronekoli.strategylab.api.ApiExceptionHandler.NoMarketDataException
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.api.BacktestResponse
import uk.kuronekoli.strategylab.api.BacktestResponse.*
import uk.kuronekoli.strategylab.market.DailyBar
import uk.kuronekoli.strategylab.market.TwseMarketDataClient

@Service
class BacktestService(
  private val marketData: TwseMarketDataClient,
  private val clock: Clock
) {
  @Autowired
  constructor(marketData: TwseMarketDataClient) : this(marketData, Clock.system(ZoneId.of("Asia/Taipei")))

  fun run(request: BacktestRequest?): BacktestResponse {
    BacktestValidation.parameters(request)
    request!!
    val symbols = symbols(request)
    val from = parseDate(request.from!!); val to = parseDate(request.to!!); val today = LocalDate.now(clock)
    if (from.isBefore(LocalDate.of(2010, 1, 1)) || from.isAfter(to) || from.isAfter(today)) throw BacktestException.input("回測日期範圍不正確；此資料服務目前提供 2010 年起的行情。")
    val effectiveTo = if (to.isAfter(today)) today else to
    val firstMonth = YearMonth.from(from); val lastMonth = YearMonth.from(effectiveTo)
    if (ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1 > 204) throw BacktestException.input("單次回測最多 17 年，請縮短期間。")
    val assets = mutableListOf<AssetPeriod>()
    val results = mutableListOf<StrategyResult>()
    val datasets = mutableListOf<DatasetManifest>()
    val taxes = LinkedHashMap<String, BigDecimal>()
    for (symbol in symbols) {
      val tax = if (request.marketTaxDefaults()) BigDecimal(if (symbol.matches(Regex("00\\d{2,4}"))) "0.001" else "0.003") else request.sellTaxRate!!
      BacktestValidation.costs(request.commissionRate!!, tax); taxes[symbol] = tax
      val loaded = marketData.load(symbol, firstMonth, lastMonth)
      BacktestValidation.bars(loaded)
      val bars = loaded.filter { !it.date.isBefore(from) && !it.date.isAfter(effectiveTo) }
      if (bars.isEmpty()) throw NoMarketDataException("$symbol 在這段期間沒有可用日行情；上市、停牌與日曆狀態未知。")
      results.addAll(BacktestEngine.run(request, symbol, bars, tax))
      val first = bars.first().date.toString(); val last = bars.last().date.toString()
      assets.add(AssetPeriod(symbol, first, last, bars.size, request.from, request.to, "UNVERIFIED_CALENDAR", "UNKNOWN"))
      datasets.add(DatasetManifest(symbol, BacktestFingerprint.dataset(symbol, bars), bars.size, first, last, "UNKNOWN", "UNSUPPORTED", "UNSUPPORTED"))
    }
    val limitations = mutableListOf(
      Limitation("HYPOTHETICAL_EXECUTION", "NEXT_CLOSE_PROXY：第 T 日收盤後訊號，使用下一筆觀察資料的收盤價代理第 T+1 日，無法保證真實成交。"),
      Limitation("DIVIDENDS_UNSUPPORTED", "未取得股利資料；未計算含息總報酬。"),
      Limitation("CORPORATE_ACTIONS_UNSUPPORTED", "未處理分割、除權與其他公司行動；使用未調整原始收盤價。"),
      Limitation("CALENDAR_STATUS_UNKNOWN", "未接入交易日曆與商品生命週期；缺漏、停牌、尚未上市及下市狀態未知。"),
      Limitation("LICENSING_UNVERIFIED", "行情授權與再散布範圍尚未確認；不得據此公開發布。"),
      Limitation("SNAPSHOT_NOT_ARCHIVED", "雜湊只識別本次使用資料，未保存原始行情快照，無法保證未來重新取得相同資料。"),
      Limitation("COSTS_SIMPLIFIED", "無最低手續費、券商折扣、滑價、交易量或價格限制模擬；整數股不代表已驗證零股成交。")
    )
    if (request.marketTaxDefaults()) limitations.add(Limitation("TAX_CLASSIFICATION_ESTIMATE", "交易稅以代碼前綴 00 分類估算（ETF 0.1%、其餘 0.3%），未驗證歷史商品類型及稅率。"))
    if (to.isAfter(today)) limitations.add(Limitation("FUTURE_END_CLAMPED", "要求迄日 $to 超過今日，計算查詢上限為 $effectiveTo；實際資料迄日另列。"))
    for (asset in assets) if (asset.from != request.from || asset.to != request.to)
      limitations.add(Limitation("COVERAGE_DIFFERS_FROM_REQUEST", "${asset.symbol} 要求 ${request.from} 至 ${request.to}，觀察到 ${asset.from} 至 ${asset.to}；無日曆不能確認邊界缺漏原因。"))
    val assumptions = listOf("日收盤價；NEXT_CLOSE_PROXY 假設研究模型", "策略 T 收盤訊號 / 下一筆觀察資料收盤代理價", "DCA 首筆及每月第一筆觀察資料投入，於該筆收盤代理價買進", "金額與費用：小數二位 HALF_UP；整數股及剩餘現金", "均價包含買入手續費；不加碼的策略持倉全部賣出", "totalReturn：期末損益／總投入；timeWeightedReturn：每日排除外部投入之 TWR", "annualizedReturn：TWR 依實際日數／365.2425 年化；maxDrawdown：TWR 正規化資產最大回撤", "股利、公司行動、交易日曆與停牌未支援；發布閘門 BLOCKED")
    val config = ResolvedConfig(symbols.toList(), request.from, request.to, effectiveTo.toString(), request.strategyOrDefault(),
      request.fastWindow!!, request.slowWindow!!, request.value(request.rsiWindow, 14), request.value(request.rsiBuyThreshold, 30.0), request.value(request.rsiSellThreshold, 55.0),
      request.value(request.bollingerWindow, 20), request.value(request.bollingerMultiplier, 2.0), request.value(request.breakoutWindow, 20), request.value(request.drawdownBuyPercent, 20.0), request.value(request.profitSellPercent, 20.0),
      AccountingPortfolio.cents(request.initialCapital!!), AccountingPortfolio.cents(request.monthlyContribution!!), request.commissionRate!!, request.sellTaxRate!!, request.marketTaxDefaults(), java.util.Collections.unmodifiableMap(LinkedHashMap(taxes)))
    val metadata = BacktestFingerprint.metadata(config, datasets)
    val primary = assets.first()
    return BacktestResponse(symbols.first(), symbols.toList(), primary.from, primary.to, primary.tradingDays, assets.toList(), "臺灣證券交易所 STOCK_DAY", assumptions, results.toList(),
      request.from, request.to, "LIMITED_RESEARCH", "BLOCKED", limitations.toList(), metadata)
  }

  private fun symbols(r: BacktestRequest): List<String> {
    val primary = r.symbol
    if (primary == null || !SYMBOL.matcher(primary.trim()).matches()) throw BacktestException.input("請提供 4 至 6 位數字的主標的代碼。")
    val raw = if (r.symbols.isNullOrEmpty()) listOf(primary) else r.symbols
    if (raw.size > 3 || raw.any { it == null || !SYMBOL.matcher(it.trim()).matches() }) throw BacktestException.input("請輸入 1 至 3 個 4 至 6 位數字的台股代碼。")
    val result = LinkedHashSet(raw.map { it!!.trim() })
    if (result.isEmpty()) throw BacktestException.input("請提供回測標的。")
    return result.toList()
  }

  private fun parseDate(value: String): LocalDate = try { LocalDate.parse(value) } catch (_: Exception) {
    throw BacktestException.input("回測日期格式不正確；請使用 YYYY-MM-DD。")
  }

  companion object { private val SYMBOL = Pattern.compile("\\d{4,6}") }
}
