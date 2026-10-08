package uk.kuronekoli.strategylab.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.market.DailyBar;

/** Shared guards are also used by direct fixture/replay execution, outside HTTP validation. */
public final class BacktestValidation {
  private BacktestValidation() {}
  public static final List<String> STRATEGIES = List.of("ma-crossover", "rsi-reversion", "bollinger-reversion", "breakout", "drawdown-entry");
  public static void parameters(BacktestRequest r) {
    if (r == null) throw BacktestException.input("請提供回測參數。");
    if (!STRATEGIES.contains(r.strategyOrDefault())) throw BacktestException.input("請選擇有效的策略範本。");
    if (r.fastWindow() == null || r.slowWindow() == null || !bounded(r.fastWindow(), 2, 500) || !bounded(r.slowWindow(), 2, 500)
        || (r.strategyOrDefault().equals("ma-crossover") && r.fastWindow() >= r.slowWindow())) throw BacktestException.input("請確認均線日數（2 至 500），短均線需小於長均線。");
    if (!bounded(r.value(r.rsiWindow(), 14), 2, 100) || !bounded(r.value(r.rsiBuyThreshold(), 30), 1, 49)
        || !bounded(r.value(r.rsiSellThreshold(), 55), 51, 99) || !bounded(r.value(r.bollingerWindow(), 20), 2, 200)
        || !bounded(r.value(r.bollingerMultiplier(), 2), 0.5, 5) || !bounded(r.value(r.breakoutWindow(), 20), 2, 250)
        || !bounded(r.value(r.drawdownBuyPercent(), 20), 1, 80) || !bounded(r.value(r.profitSellPercent(), 20), 1, 200)) throw BacktestException.input("策略參數超出可用範圍，請調整後再試。");
    money(r.initialCapital(), true, "初始資金"); money(r.monthlyContribution(), false, "每月投入");
    rate(r.commissionRate(), "手續費率"); rate(r.sellTaxRate(), "交易稅率");
    costs(r.commissionRate(), r.sellTaxRate());
  }
  public static void costs(BigDecimal commission, BigDecimal tax) {
    rate(commission, "手續費率"); rate(tax, "交易稅率");
    if (commission.add(tax).compareTo(BigDecimal.ONE) >= 0) throw BacktestException.input("賣出手續費及交易稅合計必須小於 100%。");
  }
  private static void money(BigDecimal value, boolean positive, String label) {
    if (value == null || value.compareTo(positive ? new BigDecimal("0.01") : BigDecimal.ZERO) < 0
        || value.compareTo(new BigDecimal("1000000000000")) > 0 || value.stripTrailingZeros().scale() > 2)
      throw BacktestException.input(label + "需為兩位小數以內、上限 1 兆元的有效金額。");
  }
  private static void rate(BigDecimal value, String label) {
    if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0 || value.stripTrailingZeros().scale() > 8)
      throw BacktestException.input(label + "需介於 0（含）與 1（不含），最多八位小數。");
  }
  private static boolean bounded(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }
  public static void bars(List<DailyBar> bars) {
    if (bars == null || bars.isEmpty()) throw BacktestException.data("行情資料為空，無法進行研究計算。");
    if (bars.size() > 10000) throw BacktestException.data("行情筆數超出單次研究上限。");
    LocalDate previous = null;
    for (DailyBar bar : bars) {
      if (bar == null || bar.date() == null || bar.close() == null || bar.close().signum() <= 0
          || bar.close().compareTo(new BigDecimal("1000000000")) > 0 || bar.close().compareTo(new BigDecimal("0.0001")) < 0
          || bar.close().stripTrailingZeros().scale() > 8) throw BacktestException.data("行情含空值或無效收盤價，已停止計算。");
      if (previous != null && !bar.date().isAfter(previous)) throw BacktestException.data("行情日期重複或未依時間遞增，已停止計算。");
      previous = bar.date();
    }
  }
}
