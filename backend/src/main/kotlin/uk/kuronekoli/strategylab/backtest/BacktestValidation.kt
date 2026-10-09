package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.time.LocalDate
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.market.DailyBar

/** Shared guards are also used by direct fixture/replay execution, outside HTTP validation. */
object BacktestValidation {
  val STRATEGIES = listOf("ma-crossover", "rsi-reversion", "bollinger-reversion", "breakout", "drawdown-entry")

  fun parameters(r: BacktestRequest?) {
    if (r == null) throw BacktestException.input("請提供回測參數。")
    if (!STRATEGIES.contains(r.strategyOrDefault())) throw BacktestException.input("請選擇有效的策略範本。")
    val fast = r.fastWindow
    val slow = r.slowWindow
    if (fast == null || slow == null || !bounded(fast, 2, 500) || !bounded(slow, 2, 500)
      || (r.strategyOrDefault() == "ma-crossover" && fast >= slow)) throw BacktestException.input("請確認均線日數（2 至 500），短均線需小於長均線。")
    if (!bounded(r.value(r.rsiWindow, 14), 2, 100) || !bounded(r.value(r.rsiBuyThreshold, 30.0), 1.0, 49.0)
      || !bounded(r.value(r.rsiSellThreshold, 55.0), 51.0, 99.0) || !bounded(r.value(r.bollingerWindow, 20), 2, 200)
      || !bounded(r.value(r.bollingerMultiplier, 2.0), 0.5, 5.0) || !bounded(r.value(r.breakoutWindow, 20), 2, 250)
      || !bounded(r.value(r.drawdownBuyPercent, 20.0), 1.0, 80.0) || !bounded(r.value(r.profitSellPercent, 20.0), 1.0, 200.0)) throw BacktestException.input("策略參數超出可用範圍，請調整後再試。")
    money(r.initialCapital, true, "初始資金"); money(r.monthlyContribution, false, "每月投入")
    rate(r.commissionRate, "手續費率"); rate(r.sellTaxRate, "交易稅率")
    costs(r.commissionRate!!, r.sellTaxRate!!)
  }

  fun costs(commission: BigDecimal?, tax: BigDecimal?) {
    rate(commission, "手續費率"); rate(tax, "交易稅率")
    if (commission!!.add(tax).compareTo(BigDecimal.ONE) >= 0) throw BacktestException.input("賣出手續費及交易稅合計必須小於 100%。")
  }

  private fun money(value: BigDecimal?, positive: Boolean, label: String) {
    if (value == null || value.compareTo(if (positive) BigDecimal("0.01") else BigDecimal.ZERO) < 0
      || value.compareTo(BigDecimal("1000000000000")) > 0 || value.stripTrailingZeros().scale() > 2)
      throw BacktestException.input("${label}需為兩位小數以內、上限 1 兆元的有效金額。")
  }

  private fun rate(value: BigDecimal?, label: String) {
    if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0 || value.stripTrailingZeros().scale() > 8)
      throw BacktestException.input("${label}需介於 0（含）與 1（不含），最多八位小數。")
  }

  private fun bounded(value: Int, min: Int, max: Int) = value in min..max
  private fun bounded(value: Double, min: Double, max: Double) = value.isFinite() && value >= min && value <= max

  fun bars(bars: List<DailyBar>?) {
    if (bars.isNullOrEmpty()) throw BacktestException.data("行情資料為空，無法進行研究計算。")
    if (bars.size > 10000) throw BacktestException.data("行情筆數超出單次研究上限。")
    var previous: LocalDate? = null
    for (bar in bars) {
      if (bar.date == null || bar.close == null || bar.close.signum() <= 0
        || bar.close.compareTo(BigDecimal("1000000000")) > 0 || bar.close.compareTo(BigDecimal("0.0001")) < 0
        || bar.close.stripTrailingZeros().scale() > 8) throw BacktestException.data("行情含空值或無效收盤價，已停止計算。")
      if (previous != null && !bar.date.isAfter(previous)) throw BacktestException.data("行情日期重複或未依時間遞增，已停止計算。")
      previous = bar.date
    }
  }
}
