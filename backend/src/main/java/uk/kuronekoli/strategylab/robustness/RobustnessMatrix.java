package uk.kuronekoli.strategylab.robustness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.api.BacktestResponse.StrategyResult;
import uk.kuronekoli.strategylab.backtest.BacktestEngine;
import uk.kuronekoli.strategylab.backtest.BacktestValidation;
import uk.kuronekoli.strategylab.market.DailyBar;

/** Bounded, deterministic, offline sensitivity analysis over caller-supplied bars. */
public final class RobustnessMatrix {
  public static final int MAX_CASES = 25;
  private RobustnessMatrix() {}

  /** Null values inherit the corresponding base-request value. At least one override is required. */
  public record Variant(String id, Integer fastWindow, Integer slowWindow,
      BigDecimal commissionRate, BigDecimal sellTaxRate) {}

  public record Metrics(double totalReturnPercent, double annualizedReturnPercent,
      double maxDrawdownPercent, double annualizedRealizedVolatilityPercent) {}

  public record CaseResult(String id, int sampleCount, Metrics metrics) {}
  public record Range(double min, double median, double max) {}
  public record Aggregate(Range totalReturnPercent, Range annualizedReturnPercent,
      Range maxDrawdownPercent, Range annualizedRealizedVolatilityPercent) {}
  public record Result(List<CaseResult> cases, Aggregate aggregate) {}

  /** Runs each listed variant in input order; no ranking or market access is performed. */
  public static Result run(BacktestRequest base, String symbol, List<DailyBar> suppliedBars,
      BigDecimal appliedTaxRate, List<Variant> variants) {
    if (variants == null || variants.isEmpty() || variants.size() > MAX_CASES)
      throw BacktestException.input("穩健性矩陣需包含 1 至 " + MAX_CASES + " 個案例。");
    if (base == null || symbol == null || symbol.isBlank()) throw BacktestException.input("請提供有效的回測基準與標的。");
    BacktestValidation.bars(suppliedBars);
    Set<String> ids = new HashSet<>();
    Set<String> configurations = new HashSet<>();
    List<CaseResult> results = new ArrayList<>();
    for (Variant variant : variants) {
      validateVariant(variant, base);
      if (!ids.add(variant.id())) throw BacktestException.input("穩健性矩陣案例識別碼不可重複。");
      BacktestRequest request = apply(base, variant);
      BacktestValidation.parameters(request);
      BigDecimal tax = variant.sellTaxRate() == null ? appliedTaxRate : variant.sellTaxRate();
      BacktestValidation.costs(request.commissionRate(), tax);
      String configuration = request.fastWindow() + "|" + request.slowWindow() + "|"
          + request.commissionRate().stripTrailingZeros().toPlainString() + "|" + tax.stripTrailingZeros().toPlainString();
      if (!configurations.add(configuration)) throw BacktestException.input("穩健性矩陣不可包含重複參數案例。");
      StrategyResult active = BacktestEngine.run(request, symbol, suppliedBars, tax).get(0);
      Double annualized = active.annualizedReturn();
      Double volatility = active.annualizedRealizedVolatility();
      if (annualized == null || volatility == null || !Double.isFinite(annualized) || !Double.isFinite(volatility)
          || !Double.isFinite(active.totalReturn()) || !Double.isFinite(active.maxDrawdown()))
        throw BacktestException.input("此案例的回測指標超出可表示範圍，無法納入穩健性摘要。");
      results.add(new CaseResult(variant.id(), active.dailyEquity().size(),
          new Metrics(active.totalReturn(), annualized, active.maxDrawdown(), volatility)));
    }
    return new Result(List.copyOf(results), aggregate(results));
  }

  private static void validateVariant(Variant v, BacktestRequest base) {
    if (v == null || v.id() == null || !v.id().matches("[A-Za-z0-9_-]{1,40}"))
      throw BacktestException.input("案例識別碼需為 1 至 40 個英數字、底線或連字號。");
    if (v.fastWindow() == null && v.slowWindow() == null && v.commissionRate() == null && v.sellTaxRate() == null)
      throw BacktestException.input("每個案例至少需覆寫一個策略參數或成本。");
    if ((v.fastWindow() != null || v.slowWindow() != null) && !"ma-crossover".equals(base.strategyOrDefault()))
      throw BacktestException.input("只有雙均線交叉策略可覆寫快慢均線日數。");
  }

  private static BacktestRequest apply(BacktestRequest b, Variant v) {
    return new BacktestRequest(b.symbol(), b.symbols(), b.from(), b.to(), b.strategy(),
        v.fastWindow() == null ? b.fastWindow() : v.fastWindow(),
        v.slowWindow() == null ? b.slowWindow() : v.slowWindow(), b.rsiWindow(), b.rsiBuyThreshold(),
        b.rsiSellThreshold(), b.bollingerWindow(), b.bollingerMultiplier(), b.breakoutWindow(),
        b.drawdownBuyPercent(), b.profitSellPercent(), b.monthlyContribution(), b.initialCapital(),
        v.commissionRate() == null ? b.commissionRate() : v.commissionRate(),
        v.sellTaxRate() == null ? b.sellTaxRate() : v.sellTaxRate(), b.useMarketTaxDefaults());
  }

  private static Aggregate aggregate(List<CaseResult> cases) {
    return new Aggregate(range(cases.stream().map(c -> c.metrics().totalReturnPercent()).toList()),
        range(cases.stream().map(c -> c.metrics().annualizedReturnPercent()).toList()),
        range(cases.stream().map(c -> c.metrics().maxDrawdownPercent()).toList()),
        range(cases.stream().map(c -> c.metrics().annualizedRealizedVolatilityPercent()).toList()));
  }

  private static Range range(List<Double> values) {
    List<Double> sorted = values.stream().sorted().toList();
    int n = sorted.size();
    double median = n % 2 == 1 ? sorted.get(n / 2) : sorted.get(n / 2 - 1) / 2 + sorted.get(n / 2) / 2;
    return new Range(sorted.get(0), median, sorted.get(n - 1));
  }
}
