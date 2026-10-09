package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.math.MathContext
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.market.DailyBar

/** Versioned variants use only bars at or before signalIndex. */
internal object StrategySignals {
  const val VERSION = "close-strategies-v2.0.0"
  private val MC = MathContext.DECIMAL128
  data class Signal(val buy: Boolean, val sell: Boolean, val reason: String)

  fun minimumBars(r: BacktestRequest): Int = when (r.strategyOrDefault()) {
    "ma-crossover" -> r.slowWindow!! + 2
    "rsi-reversion" -> r.value(r.rsiWindow, 14) + 2
    "bollinger-reversion" -> r.value(r.bollingerWindow, 20) + 1
    "breakout" -> r.value(r.breakoutWindow, 20) + 2
    else -> 254
  }

  fun at(r: BacktestRequest, bars: List<DailyBar>, t: Int, averageCost: BigDecimal): Signal {
    if (t < 0) return Signal(false, false, "WARMUP")
    val close = bars[t].close
    return when (r.strategyOrDefault()) {
      "ma-crossover" -> {
        if (t < r.slowWindow!!) return Signal(false, false, "WARMUP")
        val now = average(bars, t, r.fastWindow!!).compareTo(average(bars, t, r.slowWindow))
        val previous = average(bars, t - 1, r.fastWindow).compareTo(average(bars, t - 1, r.slowWindow))
        Signal(now > 0 && previous <= 0, now <= 0 && previous > 0, "SMA_CROSSING_V2")
      }
      "rsi-reversion" -> {
        val n = r.value(r.rsiWindow, 14)
        if (t < n) return Signal(false, false, "WARMUP")
        val value = rsi(bars, t, n)
        Signal(value < r.value(r.rsiBuyThreshold, 30.0), value > r.value(r.rsiSellThreshold, 55.0), "RSI_SIMPLE_ROLLING_LEVEL_V1")
      }
      "bollinger-reversion" -> {
        val n = r.value(r.bollingerWindow, 20)
        if (t < n - 1) return Signal(false, false, "WARMUP")
        val mean = average(bars, t, n)
        var variance = 0.0
        for (i in t - n + 1..t) {
          val delta = bars[i].close.subtract(mean).toDouble(); variance += delta * delta
        }
        val lower = mean.toDouble() - kotlin.math.sqrt(variance / n) * r.value(r.bollingerMultiplier, 2.0)
        Signal(close.toDouble() < lower, close.compareTo(mean) >= 0, "BOLLINGER_POPULATION_LEVEL_V1")
      }
      "breakout" -> {
        val n = r.value(r.breakoutWindow, 20)
        if (t < n) return Signal(false, false, "WARMUP")
        val priorHigh = maximum(bars, t - n, t)
        Signal(close.compareTo(priorHigh) > 0, close.compareTo(average(bars, t, n + 1)) < 0, "PRIOR_CLOSE_BREAKOUT_LEVEL_V1")
      }
      else -> {
        if (t < 252) return Signal(false, false, "WARMUP")
        val high = maximum(bars, t - 252, t)
        val buyLevel = high.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(r.value(r.drawdownBuyPercent, 20.0)).movePointLeft(2)))
        val sellLevel = averageCost.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(r.value(r.profitSellPercent, 20.0)).movePointLeft(2)))
        Signal(close.compareTo(buyLevel) <= 0, averageCost.signum() > 0 && close.compareTo(sellLevel) >= 0, "PRIOR_252_DRAWDOWN_LEVEL_V1")
      }
    }
  }

  fun average(bars: List<DailyBar>, end: Int, count: Int): BigDecimal {
    var sum = BigDecimal.ZERO
    for (i in end - count + 1..end) sum = sum.add(bars[i].close)
    return sum.divide(BigDecimal.valueOf(count.toLong()), MC)
  }

  private fun maximum(bars: List<DailyBar>, start: Int, exclusiveEnd: Int): BigDecimal {
    var high = BigDecimal.ZERO
    for (i in start until exclusiveEnd) high = high.max(bars[i].close)
    return high
  }

  private fun rsi(bars: List<DailyBar>, end: Int, count: Int): Double {
    var gain = BigDecimal.ZERO; var loss = BigDecimal.ZERO
    for (i in end - count + 1..end) {
      val change = bars[i].close.subtract(bars[i - 1].close)
      if (change.signum() > 0) gain = gain.add(change) else loss = loss.subtract(change)
    }
    if (loss.signum() == 0) return if (gain.signum() == 0) 50.0 else 100.0
    return BigDecimal.valueOf(100).multiply(gain).divide(gain.add(loss), MC).toDouble()
  }
}
