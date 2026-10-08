package uk.kuronekoli.strategylab.backtest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.market.DailyBar;

/** Versioned variants use only bars at or before signalIndex. */
final class StrategySignals {
  static final String VERSION = "close-strategies-v2.0.0";
  private static final MathContext MC = MathContext.DECIMAL128;
  record Signal(boolean buy, boolean sell, String reason) {}
  static int minimumBars(BacktestRequest r) {
    return switch (r.strategyOrDefault()) {
      case "ma-crossover" -> r.slowWindow() + 2;
      case "rsi-reversion" -> r.value(r.rsiWindow(), 14) + 2;
      case "bollinger-reversion" -> r.value(r.bollingerWindow(), 20) + 1;
      case "breakout" -> r.value(r.breakoutWindow(), 20) + 2;
      default -> 254;
    };
  }
  static Signal at(BacktestRequest r, List<DailyBar> bars, int t, BigDecimal averageCost) {
    if (t < 0) return new Signal(false, false, "WARMUP");
    BigDecimal close = bars.get(t).close();
    switch (r.strategyOrDefault()) {
      case "ma-crossover": {
        if (t < r.slowWindow()) return new Signal(false, false, "WARMUP");
        int now = average(bars, t, r.fastWindow()).compareTo(average(bars, t, r.slowWindow()));
        int previous = average(bars, t - 1, r.fastWindow()).compareTo(average(bars, t - 1, r.slowWindow()));
        return new Signal(now > 0 && previous <= 0, now <= 0 && previous > 0, "SMA_CROSSING_V2");
      }
      case "rsi-reversion": {
        int n = r.value(r.rsiWindow(), 14);
        if (t < n) return new Signal(false, false, "WARMUP");
        double value = rsi(bars, t, n);
        return new Signal(value < r.value(r.rsiBuyThreshold(), 30), value > r.value(r.rsiSellThreshold(), 55), "RSI_SIMPLE_ROLLING_LEVEL_V1");
      }
      case "bollinger-reversion": {
        int n = r.value(r.bollingerWindow(), 20);
        if (t < n - 1) return new Signal(false, false, "WARMUP");
        BigDecimal mean = average(bars, t, n);
        double variance = 0;
        for (int i = t - n + 1; i <= t; i++) {
          double delta = bars.get(i).close().subtract(mean).doubleValue(); variance += delta * delta;
        }
        double lower = mean.doubleValue() - Math.sqrt(variance / n) * r.value(r.bollingerMultiplier(), 2);
        return new Signal(close.doubleValue() < lower, close.compareTo(mean) >= 0, "BOLLINGER_POPULATION_LEVEL_V1");
      }
      case "breakout": {
        int n = r.value(r.breakoutWindow(), 20);
        if (t < n) return new Signal(false, false, "WARMUP");
        BigDecimal priorHigh = maximum(bars, t - n, t);
        // Exit mean includes the signal-day close: this is an explicitly separate close-level variant.
        return new Signal(close.compareTo(priorHigh) > 0, close.compareTo(average(bars, t, n + 1)) < 0, "PRIOR_CLOSE_BREAKOUT_LEVEL_V1");
      }
      default: {
        if (t < 252) return new Signal(false, false, "WARMUP");
        BigDecimal high = maximum(bars, t - 252, t);
        BigDecimal buyLevel = high.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(r.value(r.drawdownBuyPercent(), 20)).movePointLeft(2)));
        BigDecimal sellLevel = averageCost.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(r.value(r.profitSellPercent(), 20)).movePointLeft(2)));
        return new Signal(close.compareTo(buyLevel) <= 0, averageCost.signum() > 0 && close.compareTo(sellLevel) >= 0, "PRIOR_252_DRAWDOWN_LEVEL_V1");
      }
    }
  }
  static BigDecimal average(List<DailyBar> bars, int end, int count) {
    BigDecimal sum = BigDecimal.ZERO;
    for (int i = end - count + 1; i <= end; i++) sum = sum.add(bars.get(i).close());
    return sum.divide(BigDecimal.valueOf(count), MC);
  }
  private static BigDecimal maximum(List<DailyBar> bars, int start, int exclusiveEnd) {
    BigDecimal high = BigDecimal.ZERO;
    for (int i = start; i < exclusiveEnd; i++) high = high.max(bars.get(i).close());
    return high;
  }
  private static double rsi(List<DailyBar> bars, int end, int count) {
    BigDecimal gain = BigDecimal.ZERO, loss = BigDecimal.ZERO;
    for (int i = end - count + 1; i <= end; i++) {
      BigDecimal change = bars.get(i).close().subtract(bars.get(i - 1).close());
      if (change.signum() > 0) gain = gain.add(change); else loss = loss.subtract(change);
    }
    if (loss.signum() == 0) return gain.signum() == 0 ? 50 : 100;
    return BigDecimal.valueOf(100).multiply(gain).divide(gain.add(loss), MC).doubleValue();
  }
}
