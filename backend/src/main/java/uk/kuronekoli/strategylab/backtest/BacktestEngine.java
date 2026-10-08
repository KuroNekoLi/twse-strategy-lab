package uk.kuronekoli.strategylab.backtest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.api.BacktestResponse.Point;
import uk.kuronekoli.strategylab.api.BacktestResponse.StrategyResult;
import uk.kuronekoli.strategylab.market.DailyBar;

public final class BacktestEngine {
  private BacktestEngine() {}
  private static final String[] STRATEGY_NAMES = {"雙均線交叉", "RSI 均值回歸", "布林通道回歸", "區間突破", "回跌買進 / 獲利賣出"};

  public static List<StrategyResult> run(BacktestRequest input, String symbol, List<DailyBar> bars, double taxRate) {
    String strategy = input.strategyOrDefault();
    int lookback = switch (strategy) {
      case "drawdown-entry" -> 252;
      case "breakout" -> input.value(input.breakoutWindow(), 20) + 1;
      case "rsi-reversion" -> input.value(input.rsiWindow(), 14) + 1;
      case "bollinger-reversion" -> input.value(input.bollingerWindow(), 20);
      default -> input.slowWindow();
    };
    if (bars.size() < lookback + 2) throw new IllegalArgumentException("此期間只有 " + bars.size() + " 個交易日，資料不足以計算所選策略。");
    Portfolio dca = new Portfolio(), active = new Portfolio();
    int stride = Math.max(1, bars.size() / 320);
    for (int index = 0; index < bars.size(); index++) {
      DailyBar bar = bars.get(index);
      String month = bar.date().toString().substring(0, 7);
      boolean newMonth = !month.equals(dca.previousMonth);
      if (index == 0) {
        dca.cash += input.initialCapital().doubleValue(); dca.contributed += input.initialCapital().doubleValue();
        active.cash += input.initialCapital().doubleValue(); active.contributed += input.initialCapital().doubleValue();
      } else if (newMonth) {
        dca.cash += input.monthlyContribution().doubleValue(); dca.contributed += input.monthlyContribution().doubleValue();
        active.cash += input.monthlyContribution().doubleValue(); active.contributed += input.monthlyContribution().doubleValue();
      }
      dca.previousMonth = month;
      if (index == 0 || newMonth) buy(dca, bar.close(), input.commissionRate().doubleValue());
      int historyStart = Math.max(0, index - lookback - 1);
      List<Double> history = bars.subList(historyStart, index).stream().map(DailyBar::close).toList();
      if (history.size() >= lookback) {
        double previous = history.get(history.size() - 1);
        boolean shouldBuy = false, shouldSell = false;
        switch (strategy) {
          case "ma-crossover" -> {
            double fast = average(tail(history, input.fastWindow())), slow = average(tail(history, input.slowWindow()));
            shouldBuy = fast > slow; shouldSell = fast <= slow;
          }
          case "rsi-reversion" -> {
            double value = rsi(tail(history, input.value(input.rsiWindow(), 14) + 1));
            shouldBuy = value < input.value(input.rsiBuyThreshold(), 30); shouldSell = value > input.value(input.rsiSellThreshold(), 55);
          }
          case "bollinger-reversion" -> {
            List<Double> window = tail(history, input.value(input.bollingerWindow(), 20));
            double mean = average(window), deviation = Math.sqrt(window.stream().mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0));
            shouldBuy = previous < mean - deviation * input.value(input.bollingerMultiplier(), 2);
            shouldSell = previous >= mean;
          }
          case "breakout" -> {
            List<Double> window = tail(history, input.value(input.breakoutWindow(), 20) + 1);
            double priorHigh = window.subList(0, window.size() - 1).stream().mapToDouble(Double::doubleValue).max().orElse(0);
            shouldBuy = previous > priorHigh; shouldSell = previous < average(window);
          }
          default -> {
            double recentHigh = tail(history, Math.min(lookback, 252)).stream().mapToDouble(Double::doubleValue).max().orElse(previous);
            shouldBuy = previous <= recentHigh * (1 - input.value(input.drawdownBuyPercent(), 20) / 100);
            shouldSell = active.costBasis > 0 && previous >= active.costBasis * (1 + input.value(input.profitSellPercent(), 20) / 100);
          }
        }
        if (active.shares > 0 && shouldSell) sell(active, bar.close(), input.commissionRate().doubleValue(), taxRate);
        else if (active.shares == 0 && shouldBuy) buy(active, bar.close(), input.commissionRate().doubleValue());
      }
      double dcaValue = dca.cash + dca.shares * bar.close(), activeValue = active.cash + active.shares * bar.close();
      mark(dca, dcaValue, bar.date(), month, index, stride, bars.size());
      mark(active, activeValue, bar.date(), month, index, stride, bars.size());
    }
    DailyBar last = bars.get(bars.size() - 1);
    double years = Math.max(Duration.between(bars.get(0).date().atStartOfDay(), last.date().atStartOfDay()).toDays() / 365.2425, 1 / 365.2425);
    String label = strategyLabel(input);
    return List.of(finish(symbol + "-" + strategy, symbol + " · " + label, active, active.cash + active.shares * last.close(), years),
        finish(symbol + "-dca", symbol + " · 定期定額", dca, dca.cash + dca.shares * last.close(), years));
  }

  private static String strategyLabel(BacktestRequest in) {
    return switch (in.strategyOrDefault()) {
      case "ma-crossover" -> STRATEGY_NAMES[0] + " · " + in.fastWindow() + "/" + in.slowWindow() + " 日";
      case "rsi-reversion" -> STRATEGY_NAMES[1] + " · RSI" + in.value(in.rsiWindow(), 14) + " < " + in.value(in.rsiBuyThreshold(), 30) + " 買 / > " + in.value(in.rsiSellThreshold(), 55) + " 賣";
      case "bollinger-reversion" -> STRATEGY_NAMES[2] + " · " + in.value(in.bollingerWindow(), 20) + " 日 / " + in.value(in.bollingerMultiplier(), 2) + "σ";
      case "breakout" -> STRATEGY_NAMES[3] + " · 突破 " + in.value(in.breakoutWindow(), 20) + " 日高點";
      default -> STRATEGY_NAMES[4] + " · 回跌 " + in.value(in.drawdownBuyPercent(), 20) + "% 買 / 獲利 " + in.value(in.profitSellPercent(), 20) + "% 賣";
    };
  }
  private static void mark(Portfolio p, double value, LocalDate date, String month, int index, int stride, int size) {
    p.peak = Math.max(p.peak, value);
    if (p.peak > 0) p.maxDrawdown = Math.max(p.maxDrawdown, (p.peak - value) / p.peak);
    if (index % stride == 0 || index == size - 1) p.points.add(new Point(month, Math.round(value)));
  }
  private static StrategyResult finish(String key, String name, Portfolio p, double value, double years) {
    double total = p.contributed > 0 ? value / p.contributed * 100 - 100 : 0;
    double annual = value > 0 && p.contributed > 0 ? (Math.pow(value / p.contributed, 1 / years) - 1) * 100 : -100;
    return new StrategyResult(key, name, Math.round(value), Math.round(p.contributed), total, annual, p.maxDrawdown * 100, List.copyOf(p.points));
  }
  private static void buy(Portfolio p, double price, double fee) {
    if (p.cash <= 0 || price <= 0) return;
    double units = p.cash / (price * (1 + fee));
    p.costBasis = (p.costBasis * p.shares + price * units) / (p.shares + units); p.cash = 0; p.shares += units;
  }
  private static void sell(Portfolio p, double price, double fee, double tax) {
    if (p.shares <= 0) return;
    p.cash += p.shares * price * (1 - fee - tax); p.shares = 0; p.costBasis = 0;
  }
  private static double average(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).average().orElse(0); }
  private static List<Double> tail(List<Double> values, int count) { return values.subList(Math.max(0, values.size() - count), values.size()); }
  private static double rsi(List<Double> values) {
    double gain = 0, loss = 0;
    for (int i = 1; i < values.size(); i++) { double change = values.get(i) - values.get(i - 1); gain += Math.max(change, 0); loss += Math.max(-change, 0); }
    gain /= values.size() - 1; loss /= values.size() - 1;
    if (loss == 0) return gain == 0 ? 50 : 100;
    return 100 - 100 / (1 + gain / loss);
  }
  private static final class Portfolio {
    double cash, shares, contributed, peak, maxDrawdown, costBasis; String previousMonth = ""; List<Point> points = new ArrayList<>();
  }
}
