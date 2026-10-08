package uk.kuronekoli.strategylab.backtest;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.api.BacktestResponse.StrategyResult;
import uk.kuronekoli.strategylab.market.DailyBar;

/** Stateless deterministic close-only research simulation. Never a real execution promise. */
public final class BacktestEngine {
  public static final String VERSION = "m1-engine-v1.0.0";
  public static final String EXECUTION_MODEL = "NEXT_CLOSE_PROXY";
  public static final String EXECUTION_VERSION = "next-close-proxy-v1.0.0";
  public static final String COST_VERSION = "whole-shares-cents-half-up-v1.0.0";
  public static final String ADJUSTMENT = "RAW_CLOSE_UNADJUSTED_V1";
  public static final String RULES_VERSION = "research-estimated-tax-v1.0.0";
  public static final String METRICS_VERSION = "daily-twr-volatility-beginning-flow-v2.0.0";
  private BacktestEngine() {}
  public static List<StrategyResult> run(BacktestRequest input, String symbol, List<DailyBar> bars, double taxRate) {
    if (!Double.isFinite(taxRate)) throw BacktestException.input("交易稅率需為有限數值。");
    return run(input, symbol, bars, BigDecimal.valueOf(taxRate));
  }
  public static List<StrategyResult> run(BacktestRequest input, String symbol, List<DailyBar> bars, BigDecimal taxRate) {
    BacktestValidation.parameters(input); BacktestValidation.costs(input.commissionRate(), taxRate); BacktestValidation.bars(bars);
    if (bars.size() < StrategySignals.minimumBars(input)) throw new BacktestException("INSUFFICIENT_DATA", "此期間只有 " + bars.size() + " 個交易日，資料不足以計算所選策略。", 422);
    AccountingPortfolio dca = new AccountingPortfolio(), active = new AccountingPortfolio(), buyAndHold = new AccountingPortfolio();
    YearMonth previousMonth = null;
    for (int i = 0; i < bars.size(); i++) {
      DailyBar bar = bars.get(i);
      YearMonth month = YearMonth.from(bar.date());
      boolean monthStart = !month.equals(previousMonth);
      BigDecimal flow = i == 0 ? input.initialCapital() : monthStart ? input.monthlyContribution() : BigDecimal.ZERO;
      dca.deposit(flow); active.deposit(flow);
      buyAndHold.deposit(flow);
      if (i == 0 || monthStart) dca.buy(null, bar.date(), bar.close(), input.commissionRate(), "DCA_FIRST_OBSERVED_BAR_OF_MONTH_V1");
      if (i == 0) buyAndHold.buy(null, bar.date(), bar.close(), input.commissionRate(), "BUY_AND_HOLD_INITIAL_CAPITAL_FIRST_OBSERVED_CLOSE_V1");
      StrategySignals.Signal signal = StrategySignals.at(input, bars, i - 1, active.averageCost());
      if (active.shares > 0 && signal.sell()) active.sell(bars.get(i - 1).date(), bar.date(), bar.close(), input.commissionRate(), taxRate, signal.reason());
      else if (active.shares == 0 && signal.buy()) active.buy(bars.get(i - 1).date(), bar.date(), bar.close(), input.commissionRate(), signal.reason());
      dca.mark(bar.date(), bar.close(), flow); active.mark(bar.date(), bar.close(), flow);
      buyAndHold.mark(bar.date(), bar.close(), flow); previousMonth = month;
    }
    double years = ChronoUnit.DAYS.between(bars.get(0).date(), bars.get(bars.size() - 1).date()) / 365.2425;
    return List.of(finish(symbol + "-" + input.strategyOrDefault(), symbol + " · " + label(input), active, years),
        finish(symbol + "-dca", symbol + " · 定期定額", dca, years),
        finish(symbol + "-buy-and-hold", symbol + " · 買進持有（後續月投入保留現金）", buyAndHold, years));
  }
  private static StrategyResult finish(String key, String name, AccountingPortfolio p, double years) {
    BigDecimal ending = p.previousEquity, profit = ending.subtract(p.contributed);
    double investedProfit = profit.divide(p.contributed, java.math.MathContext.DECIMAL128).multiply(BigDecimal.valueOf(100)).doubleValue();
    Double twr = p.nav.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).doubleValue();
    if (!Double.isFinite(twr)) { twr = null; p.metricWarnings.add("TWR_NUMERIC_RANGE"); }
    double annualized = Math.expm1(Math.log(p.nav.doubleValue()) / years) * 100;
    // Very short extreme-return fixtures can overflow annualization; this is explicitly unavailable, never infinity JSON.
    Double reportedAnnualized = annualized;
    if (!Double.isFinite(annualized)) { reportedAnnualized = null; p.metricWarnings.add("ANNUALIZATION_NUMERIC_RANGE"); }
    Double realizedVolatility = annualizedRealizedVolatility(p.days.stream().map(day -> day.dailyReturn()).toList());
    if (realizedVolatility == null && p.days.stream().map(day -> day.dailyReturn())
        .filter(value -> value != null && Double.isFinite(value)).count() >= 2)
      p.metricWarnings.add("REALIZED_VOLATILITY_NUMERIC_RANGE");
    return new StrategyResult(key, name, ending, p.contributed, investedProfit, reportedAnnualized,
        p.maxDrawdown.multiply(BigDecimal.valueOf(100)).doubleValue(), List.copyOf(p.points), profit, twr,
        "DAILY_TWR_ACT_365_2425", List.copyOf(p.trades), List.copyOf(p.days), List.copyOf(p.metricWarnings), realizedVolatility);
  }
  /** Sample standard deviation of available daily percentage returns, annualized using 252 sessions. */
  static Double annualizedRealizedVolatility(List<Double> dailyReturns) {
    long count = 0;
    double mean = 0, sumSquaredDifferences = 0;
    for (Double value : dailyReturns) {
      if (value == null || !Double.isFinite(value)) continue;
      count++;
      double difference = value - mean;
      mean += difference / count;
      sumSquaredDifferences += difference * (value - mean);
    }
    if (count < 2) return null;
    double volatility = Math.sqrt(Math.max(0, sumSquaredDifferences / (count - 1))) * Math.sqrt(252);
    return Double.isFinite(volatility) ? volatility : null;
  }
  private static String label(BacktestRequest r) {
    return switch (r.strategyOrDefault()) {
      case "ma-crossover" -> "雙均線交叉 · " + r.fastWindow() + "/" + r.slowWindow() + " 日";
      case "rsi-reversion" -> "RSI 簡單滾動均值回歸（門檻狀態）";
      case "bollinger-reversion" -> "布林通道回歸（母體標準差、門檻狀態）";
      case "breakout" -> "前期最高收盤價突破";
      default -> "前 252 日回跌買進 / 含費成本獲利賣出";
    };
  }
}
