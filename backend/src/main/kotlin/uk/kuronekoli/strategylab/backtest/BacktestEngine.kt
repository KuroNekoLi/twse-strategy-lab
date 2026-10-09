package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.math.MathContext
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.api.BacktestResponse.StrategyResult
import uk.kuronekoli.strategylab.market.DailyBar

/** Stateless deterministic close-only research simulation. Never a real execution promise. */
object BacktestEngine {
  const val VERSION = "m1-engine-v1.0.0"
  const val EXECUTION_MODEL = "NEXT_CLOSE_PROXY"
  const val EXECUTION_VERSION = "next-close-proxy-v1.0.0"
  const val COST_VERSION = "whole-shares-cents-half-up-v1.0.0"
  const val ADJUSTMENT = "RAW_CLOSE_UNADJUSTED_V1"
  const val RULES_VERSION = "research-estimated-tax-v1.0.0"
  const val METRICS_VERSION = "daily-twr-volatility-beginning-flow-v2.0.0"

  fun run(input: BacktestRequest, symbol: String, bars: List<DailyBar>, taxRate: Double): List<StrategyResult> {
    if (!taxRate.isFinite()) throw BacktestException.input("交易稅率需為有限數值。")
    return run(input, symbol, bars, BigDecimal.valueOf(taxRate))
  }

  fun run(input: BacktestRequest, symbol: String, bars: List<DailyBar>, taxRate: BigDecimal): List<StrategyResult> {
    BacktestValidation.parameters(input)
    BacktestValidation.costs(input.commissionRate, taxRate)
    BacktestValidation.bars(bars)
    if (bars.size < StrategySignals.minimumBars(input)) throw BacktestException("INSUFFICIENT_DATA", "此期間只有 ${bars.size} 個交易日，資料不足以計算所選策略。", 422)
    val dca = AccountingPortfolio(); val active = AccountingPortfolio(); val buyAndHold = AccountingPortfolio()
    var previousMonth: YearMonth? = null
    for (i in bars.indices) {
      val bar = bars[i]
      val month = YearMonth.from(bar.date)
      val monthStart = month != previousMonth
      val flow = if (i == 0) input.initialCapital!! else if (monthStart) input.monthlyContribution!! else BigDecimal.ZERO
      dca.deposit(flow); active.deposit(flow); buyAndHold.deposit(flow)
      if (i == 0 || monthStart) dca.buy(null, bar.date, bar.close, input.commissionRate!!, "DCA_FIRST_OBSERVED_BAR_OF_MONTH_V1")
      if (i == 0) buyAndHold.buy(null, bar.date, bar.close, input.commissionRate!!, "BUY_AND_HOLD_INITIAL_CAPITAL_FIRST_OBSERVED_CLOSE_V1")
      val signal = StrategySignals.at(input, bars, i - 1, active.averageCost())
      if (active.shares > 0 && signal.sell) active.sell(bars[i - 1].date, bar.date, bar.close, input.commissionRate!!, taxRate, signal.reason)
      else if (active.shares == 0L && signal.buy) active.buy(bars[i - 1].date, bar.date, bar.close, input.commissionRate!!, signal.reason)
      dca.mark(bar.date, bar.close, flow); active.mark(bar.date, bar.close, flow); buyAndHold.mark(bar.date, bar.close, flow)
      previousMonth = month
    }
    val years = ChronoUnit.DAYS.between(bars.first().date, bars.last().date) / 365.2425
    return listOf(
      finish("$symbol-${input.strategyOrDefault()}", "$symbol · ${label(input)}", active, years),
      finish("$symbol-dca", "$symbol · 定期定額", dca, years),
      finish("$symbol-buy-and-hold", "$symbol · 買進持有（後續月投入保留現金）", buyAndHold, years)
    )
  }

  private fun finish(key: String, name: String, p: AccountingPortfolio, years: Double): StrategyResult {
    val ending = p.previousEquity!!
    val profit = ending.subtract(p.contributed)
    val investedProfit = profit.divide(p.contributed, MathContext.DECIMAL128).multiply(BigDecimal.valueOf(100)).toDouble()
    var twr: Double? = p.nav.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).toDouble()
    if (twr?.isFinite() != true) { twr = null; p.metricWarnings.add("TWR_NUMERIC_RANGE") }
    val annualized = Math.expm1(Math.log(p.nav.toDouble()) / years) * 100
    var reportedAnnualized: Double? = annualized
    if (!annualized.isFinite()) { reportedAnnualized = null; p.metricWarnings.add("ANNUALIZATION_NUMERIC_RANGE") }
    val dailyReturns = p.days.map { it.dailyReturn }
    val realizedVolatility = annualizedRealizedVolatility(dailyReturns)
    if (realizedVolatility == null && dailyReturns.count { it != null && it.isFinite() } >= 2)
      p.metricWarnings.add("REALIZED_VOLATILITY_NUMERIC_RANGE")
    return StrategyResult(key, name, ending, p.contributed, investedProfit, reportedAnnualized,
      p.maxDrawdown.multiply(BigDecimal.valueOf(100)).toDouble(), p.points.toList(), profit, twr,
      "DAILY_TWR_ACT_365_2425", p.trades.toList(), p.days.toList(), p.metricWarnings.toList(), realizedVolatility)
  }

  /** Sample standard deviation of available daily percentage returns, annualized using 252 sessions. */
  internal fun annualizedRealizedVolatility(dailyReturns: List<Double?>): Double? {
    var count = 0L; var mean = 0.0; var sumSquaredDifferences = 0.0
    for (value in dailyReturns) {
      if (value == null || !value.isFinite()) continue
      count++
      val difference = value - mean
      mean += difference / count
      sumSquaredDifferences += difference * (value - mean)
    }
    if (count < 2) return null
    val volatility = kotlin.math.sqrt(kotlin.math.max(0.0, sumSquaredDifferences / (count - 1))) * kotlin.math.sqrt(252.0)
    return if (volatility.isFinite()) volatility else null
  }

  private fun label(r: BacktestRequest): String = when (r.strategyOrDefault()) {
    "ma-crossover" -> "雙均線交叉 · ${r.fastWindow}/${r.slowWindow} 日"
    "rsi-reversion" -> "RSI 簡單滾動均值回歸（門檻狀態）"
    "bollinger-reversion" -> "布林通道回歸（母體標準差、門檻狀態）"
    "breakout" -> "前期最高收盤價突破"
    else -> "前 252 日回跌買進 / 含費成本獲利賣出"
  }
}
