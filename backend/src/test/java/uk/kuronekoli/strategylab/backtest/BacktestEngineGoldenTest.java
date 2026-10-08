package uk.kuronekoli.strategylab.backtest;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.api.BacktestResponse.StrategyResult;
import uk.kuronekoli.strategylab.api.BacktestResponse.Trade;
import uk.kuronekoli.strategylab.market.DailyBar;

class BacktestEngineGoldenTest {
  static BigDecimal bd(String n) { return new BigDecimal(n); }
  static BacktestRequest request(String strategy, String capital, String monthly, String fee, String tax) {
    return new BacktestRequest("2330", null, "2024-01-01", "2024-12-31", strategy, 2, 3, 2, 30d, 55d,
        3, 0.5d, 2, 20d, 20d, bd(monthly), bd(capital), bd(fee), bd(tax), false);
  }
  static List<DailyBar> prices(double... prices) { return prices(LocalDate.of(2024, 1, 1), prices); }
  static List<DailyBar> prices(LocalDate start, double... prices) {
    List<DailyBar> bars = new ArrayList<>();
    for (int i = 0; i < prices.length; i++) bars.add(new DailyBar(start.plusDays(i), prices[i]));
    return bars;
  }
  static List<StrategyResult> run(BacktestRequest r, List<DailyBar> bars) { return BacktestEngine.run(r, "2330", bars, r.sellTaxRate()); }
  static void money(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual), expected + " vs " + actual); }
  private static BacktestRequest ma() { return request("ma-crossover", "1000", "0", "0", "0"); }

  @Test void risingPricesDoNotInventCrossingAndDcaGrowsFortyPercent() {
    var results = run(ma(), prices(100, 110, 120, 130, 140));
    money("1000", results.get(0).endingValue()); assertTrue(results.get(0).trades().isEmpty());
    var dca = results.get(1); money("1400", dca.endingValue()); assertEquals(40, dca.timeWeightedReturn(), 1e-10); assertEquals(0, dca.maxDrawdown(), 1e-10);
  }
  @Test void annualizedRealizedVolatilityUsesSampleDeviationOfDailyPercentReturns() {
    var result = run(request("ma-crossover", "10000", "0", "0", "0"), prices(100, 110, 99, 108.9, 108.9)).get(2);
    // Independently derived daily percentages: 0, +10, -10, +10, 0; sample variance = 280 / 4 = 70.
    assertEquals(Math.sqrt(70 * 252), result.annualizedRealizedVolatility(), 1e-10);
    assertEquals(0, BacktestEngine.annualizedRealizedVolatility(List.of(0d, 0d)), 0);
  }
  @Test void realizedVolatilityIgnoresUnavailableReturnsAndRequiresTwoValidObservations() {
    assertNull(BacktestEngine.annualizedRealizedVolatility(Arrays.asList(null, 10d)));
    assertNull(BacktestEngine.annualizedRealizedVolatility(Arrays.asList(null, 10d, Double.NaN)));
    assertEquals(Math.sqrt(50400), BacktestEngine.annualizedRealizedVolatility(Arrays.asList(null, 10d, -10d)), 1e-10);
  }
  @Test void buyAndHoldInvestsInitialCapitalOnceAndKeepsLaterMonthlyFlowsAsCash() {
    var result = run(request("ma-crossover", "1000", "500", "0.01", "0"),
        List.of(new DailyBar(LocalDate.of(2024, 1, 31), 100),
            new DailyBar(LocalDate.of(2024, 2, 1), 200),
            new DailyBar(LocalDate.of(2024, 2, 2), 200),
            new DailyBar(LocalDate.of(2024, 3, 1), 50),
            new DailyBar(LocalDate.of(2024, 3, 2), 50))).get(2);

    assertEquals("2330-buy-and-hold", result.key());
    assertEquals("2330 · 買進持有（後續月投入保留現金）", result.name());
    assertEquals(1, result.trades().size());
    Trade initial = result.trades().get(0);
    assertEquals("BUY_AND_HOLD_INITIAL_CAPITAL_FIRST_OBSERVED_CLOSE_V1", initial.reason());
    assertEquals(9, initial.quantity()); money("900", initial.gross()); money("9", initial.fee());
    money("91", initial.cashAfter());
    assertEquals(9, result.dailyEquity().get(0).shares());
    money("91", result.dailyEquity().get(0).cash());
    assertEquals(9, result.dailyEquity().get(1).shares());
    money("591", result.dailyEquity().get(1).cash());
    assertEquals(9, result.dailyEquity().get(3).shares());
    money("1091", result.dailyEquity().get(3).cash());
    money("1541", result.endingValue()); money("2000", result.contributed());
    money("-459", result.profit());
    assertEquals(500, result.dailyEquity().get(1).externalFlow().doubleValue());
    assertEquals(500, result.dailyEquity().get(3).externalFlow().doubleValue());
  }
  @Test void fallingAndFlatPricesHaveHandwrittenReturns() {
    var falling = run(ma(), prices(100, 90, 80, 70, 60)).get(1);
    money("600", falling.endingValue()); assertEquals(-40, falling.timeWeightedReturn(), 1e-10); assertEquals(40, falling.maxDrawdown(), 1e-10);
    var flat = run(ma(), prices(100, 100, 100, 100, 100)).get(1);
    money("1000", flat.endingValue()); assertEquals(0, flat.timeWeightedReturn(), 1e-10); assertEquals(0, flat.annualizedReturn(), 1e-10);
  }
  @Test void goldenAndDeathCrossExecuteOnlyAtNextObservedClose() {
    var active = run(ma(), prices(100, 100, 100, 120, 110, 80, 90, 100)).get(0);
    assertEquals(2, active.trades().size());
    Trade buy = active.trades().get(0), sell = active.trades().get(1);
    assertEquals("2024-01-04", buy.signalDate()); assertEquals("2024-01-05", buy.executionDate());
    assertEquals(9, buy.quantity()); money("110", buy.price()); money("10", buy.cashAfter());
    assertEquals("2024-01-06", sell.signalDate()); assertEquals("2024-01-07", sell.executionDate()); money("90", sell.price());
    money("820", active.endingValue()); assertEquals(-18, active.timeWeightedReturn(), 1e-10);
    // The final-day golden cross has no next bar and cannot create a same-day fill.
    assertEquals(0, active.dailyEquity().get(7).shares());
  }
  @Test void feesTaxResidualCashAndFeeInclusiveCostMatchIndependentArithmetic() {
    var active = run(request("ma-crossover", "1000", "0", "0.01", "0.003"), prices(100, 100, 100, 120, 100, 80, 110)).get(0);
    Trade buy = active.trades().get(0), sell = active.trades().get(1);
    // 9 * 100 = 900; fee = 9; cash = 91; average cost = 909 / 9 = 101.
    assertEquals(9, buy.quantity()); money("900", buy.gross()); money("9", buy.fee()); money("91", buy.cashAfter()); money("101", buy.averageCostAfter());
    // Sell gross 990; fee 9.90; tax 2.97; remaining cash 91 + 990 - 9.90 - 2.97 = 1068.13.
    money("990", sell.gross()); money("9.90", sell.fee()); money("2.97", sell.tax()); money("1068.13", sell.cashAfter());
    money("1068.13", active.endingValue()); money("68.13", active.profit()); assertEquals(6.813, active.timeWeightedReturn(), 1e-10);
  }
  @Test void feeRoundingUsesCentsHalfUpAndFirstDayCostIsTwrLoss() {
    var dca = run(request("ma-crossover", "1000", "0", "0.01", "0"), prices(100, 100, 100, 100, 100)).get(1);
    money("991", dca.endingValue()); assertEquals(-0.9, dca.timeWeightedReturn(), 1e-10); assertEquals(0.9, dca.maxDrawdown(), 1e-10);
    AccountingPortfolio p = new AccountingPortfolio(); p.deposit(bd("10.01"));
    p.buy(null, LocalDate.of(2024, 1, 1), bd("10"), bd("0.0005"), "SYNTHETIC");
    assertEquals(1, p.shares); money("0.01", p.trades.get(0).fee()); money("0", p.cash);
    p.sell(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), bd("10"), bd("0.0005"), bd("0.0005"), "SYNTHETIC");
    money("9.98", p.cash); money("0.01", p.trades.get(1).tax());
  }

  @Test void subCentGrossCannotCreateFreeSharesAfterCashIsExhausted() {
    AccountingPortfolio p = new AccountingPortfolio(); p.deposit(bd("0.01"));
    p.buy(null, LocalDate.of(2024, 1, 31), bd("0.01"), bd("0"), "SYNTHETIC");
    assertEquals(1, p.shares); money("0", p.cash);
    p.mark(LocalDate.of(2024, 1, 31), bd("0.01"), bd("0.01"));
    p.buy(null, LocalDate.of(2024, 2, 1), bd("0.0001"), bd("0"), "SYNTHETIC");
    Trade skipped = p.trades.get(1);
    assertEquals("SKIPPED_INSUFFICIENT_CASH", skipped.status()); assertEquals(0, skipped.quantity());
    money("0", skipped.gross()); money("0", p.cash); assertEquals(1, p.shares);
    p.mark(LocalDate.of(2024, 2, 1), bd("1"), bd("0"));
    money("1", p.days.get(1).equity());
  }
  @Test void roundedAffordabilityCanBuyAWholeShareWithoutTheoreticalFractionalFee() {
    AccountingPortfolio p = new AccountingPortfolio(); p.deposit(bd("10.00"));
    p.buy(null, LocalDate.of(2024, 1, 1), bd("10"), bd("0.0001"), "SYNTHETIC");
    assertEquals(1, p.shares); money("0", p.cash); money("0", p.trades.get(0).fee());
  }
  @Test void cashShortageCreatesAnExplicitSkippedIntentAndNoFractionalShares() {
    var results = run(request("ma-crossover", "50", "0", "0", "0"), prices(100, 100, 100, 120, 100));
    for (StrategyResult r : results) {
      money("50", r.endingValue()); assertEquals(0, r.trades().get(0).quantity());
      assertEquals("SKIPPED_INSUFFICIENT_CASH", r.trades().get(0).status()); assertEquals(0, r.dailyEquity().get(4).shares());
    }
  }
  @Test void monthlyDepositCannotHideTenPercentDrawdown() {
    var dca = run(request("ma-crossover", "1000", "1000", "0", "0"), prices(LocalDate.of(2024, 1, 27), 100, 100, 100, 90, 90, 90, 90)).get(1);
    // 10 initial shares lose 100. Deposit 1000 buys 11 more shares at 90, leaves 10 cash.
    money("2000", dca.contributed()); money("1900", dca.endingValue()); assertEquals(-5, dca.totalReturn(), 1e-10);
    assertEquals(-10, dca.timeWeightedReturn(), 1e-10); assertEquals(10, dca.maxDrawdown(), 1e-10);
    var depositDay = dca.dailyEquity().get(5); money("1000", depositDay.externalFlow()); money("1900", depositDay.equity());
    money("0.9", depositDay.normalizedNav()); assertEquals(0, depositDay.dailyReturn(), 1e-10);
    double annual = (Math.pow(0.9, 365.2425 / 6) - 1) * 100;
    assertEquals(annual, dca.annualizedReturn(), 1e-10);
  }
  @Test void rsiSimpleRollingLevelHasKnownBuyAndSellDates() {
    var active = run(request("rsi-reversion", "1000", "0", "0", "0"), prices(100, 90, 80, 70, 100, 110)).get(0);
    // Signal RSI(2)=0 on Jan3; buy Jan4 14@70, cash20. RSI(2)=75 Jan5; sell Jan6 14@110.
    assertEquals(2, active.trades().size()); assertEquals("2024-01-03", active.trades().get(0).signalDate());
    assertEquals("2024-01-04", active.trades().get(0).executionDate()); assertEquals(14, active.trades().get(0).quantity());
    assertEquals("2024-01-05", active.trades().get(1).signalDate()); money("1560", active.endingValue());
  }
  @Test void bollingerUsesPopulationDeviationAndLevelThreshold() {
    var active = run(request("bollinger-reversion", "1000", "0", "0", "0"), prices(100, 90, 80, 90, 100)).get(0);
    // Jan3 mean90, population sigma sqrt(200/3), lower85.9175; close80 below. Jan4 mean86.6667, close90 above middle.
    assertEquals(2, active.trades().size()); assertEquals("2024-01-03", active.trades().get(0).signalDate());
    assertEquals(11, active.trades().get(0).quantity()); assertEquals("2024-01-05", active.trades().get(1).executionDate()); money("1110", active.endingValue());
  }
  @Test void breakoutHighExcludesSignalDayAndExitUsesItsMean() {
    var active = run(request("breakout", "1000", "0", "0", "0"), prices(100, 100, 120, 130, 80, 90)).get(0);
    // Jan3 close120 > prior2 high100; buy Jan4 7@130, cash90. Jan5 80 < mean(120,130,80)=110.
    assertEquals(2, active.trades().size()); assertEquals("2024-01-03", active.trades().get(0).signalDate());
    assertEquals(7, active.trades().get(0).quantity()); assertEquals("2024-01-06", active.trades().get(1).executionDate()); money("720", active.endingValue());
  }
  @Test void drawdownPrior252ExcludesSignalDayAndRetainsOldestPriorHigh() {
    double[] values = new double[256]; Arrays.fill(values, 100); values[0] = 200;
    values[252] = 150; values[253] = 100; values[254] = 130; values[255] = 110;
    var bars = prices(values); var active = run(request("drawdown-entry", "1000", "0", "0", "0"), bars).get(0);
    // Prior252 contains index0 high200: signal150 <=160. A window including signal would lose index0 and incorrectly miss the buy.
    assertEquals(2, active.trades().size()); assertEquals(bars.get(252).date().toString(), active.trades().get(0).signalDate());
    assertEquals(bars.get(253).date().toString(), active.trades().get(0).executionDate()); assertEquals(10, active.trades().get(0).quantity());
    assertEquals(bars.get(254).date().toString(), active.trades().get(1).signalDate()); money("1100", active.endingValue());
  }
  @Test void identicalCapturedBarsReplayExactlyAndFutureSuffixCannotChangePast() {
    var bars = prices(100, 100, 100, 120, 110, 80, 90, 100);
    assertEquals(run(ma(), bars), run(ma(), List.copyOf(bars)));
    List<DailyBar> extended = new ArrayList<>(bars); extended.add(new DailyBar(LocalDate.of(2024, 1, 9), 10000)); extended.add(new DailyBar(LocalDate.of(2024, 1, 10), 1));
    for (int strategyIndex = 0; strategyIndex < 2; strategyIndex++) {
      var before = run(ma(), bars).get(strategyIndex); var after = run(ma(), extended).get(strategyIndex);
      assertEquals(before.dailyEquity(), after.dailyEquity().subList(0, bars.size()));
      assertEquals(before.series(), after.series().subList(0, bars.size()));
      assertEquals(before.trades(), after.trades().stream().filter(t -> LocalDate.parse(t.executionDate()).isBefore(LocalDate.of(2024, 1, 9))).toList());
    }
  }
  @Test void ledgerCanBeRebuiltFromIndependentCashAndPositionEquations() {
    var bars = prices(LocalDate.of(2024, 1, 27), 100, 100, 100, 120, 100, 80, 110, 100, 120);
    for (var result : run(request("ma-crossover", "1000", "100", "0.01", "0.003"), bars)) {
      BigDecimal cash = bd("0"), contributed = bd("0"), basis = bd("0"); long shares = 0;
      for (int i = 0; i < bars.size(); i++) {
        var day = result.dailyEquity().get(i); cash = cash.add(day.externalFlow()); contributed = contributed.add(day.externalFlow());
        for (var trade : result.trades()) if (trade.executionDate().equals(day.date())) {
          if (trade.side().equals("BUY")) { cash = cash.subtract(trade.gross()).subtract(trade.fee()); shares += trade.quantity(); basis = basis.add(trade.gross()).add(trade.fee()); }
          else { cash = cash.add(trade.gross()).subtract(trade.fee()).subtract(trade.tax()); shares -= trade.quantity(); basis = bd("0"); }
          assertEquals(0, cash.compareTo(trade.cashAfter())); assertEquals(shares, trade.positionAfter());
        }
        assertEquals(0, cash.compareTo(day.cash())); assertEquals(shares, day.shares()); assertTrue(cash.signum() >= 0);
        assertEquals(0, contributed.compareTo(day.contributed())); assertEquals(0, basis.compareTo(day.positionCost()));
        BigDecimal equity = cash.add(bars.get(i).close().multiply(BigDecimal.valueOf(shares)).setScale(2, RoundingMode.HALF_UP));
        assertEquals(0, equity.compareTo(day.equity()));
      }
      assertEquals(0, result.endingValue().subtract(result.contributed()).compareTo(result.profit()));
    }
  }
  @Test void futureSuffixInvarianceAppliesToEveryVersionedStrategy() {
    double[] longDrawdown = new double[256]; Arrays.fill(longDrawdown, 100); longDrawdown[0] = 200;
    longDrawdown[252] = 150; longDrawdown[253] = 100; longDrawdown[254] = 130; longDrawdown[255] = 110;
    var fixtures = java.util.Map.of("ma-crossover", prices(100, 100, 100, 120, 110, 80, 90, 100),
        "rsi-reversion", prices(100, 90, 80, 70, 100, 110), "bollinger-reversion", prices(100, 90, 80, 90, 100),
        "breakout", prices(100, 100, 120, 130, 80, 90), "drawdown-entry", prices(longDrawdown));
    for (var entry : fixtures.entrySet()) {
      var r = request(entry.getKey(), "1000", "0", "0", "0"); var prefix = entry.getValue();
      var extended = new ArrayList<>(prefix); var firstFuture = prefix.get(prefix.size() - 1).date().plusDays(1);
      extended.add(new DailyBar(firstFuture, 10000)); extended.add(new DailyBar(firstFuture.plusDays(1), 1));
      for (int index = 0; index < 2; index++) {
        var before = run(r, prefix).get(index); var after = run(r, extended).get(index);
        assertEquals(before.dailyEquity(), after.dailyEquity().subList(0, prefix.size()), entry.getKey());
        assertEquals(before.trades(), after.trades().stream().filter(t -> LocalDate.parse(t.executionDate()).isBefore(firstFuture)).toList(), entry.getKey());
      }
    }
  }
  @Test void zeroEquityFollowingRoundedSellCostsMakesSubsequentDailyReturnUnavailable() {
    AccountingPortfolio p = new AccountingPortfolio(); p.deposit(bd("0.01"));
    p.buy(null, LocalDate.of(2024, 1, 1), bd("0.01"), bd("0"), "SYNTHETIC"); p.mark(LocalDate.of(2024, 1, 1), bd("0.01"), bd("0.01"));
    // Gross .01, fee .0099 rounds .01, no remaining cash or shares.
    p.sell(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), bd("0.01"), bd("0.99"), bd("0"), "SYNTHETIC");
    p.mark(LocalDate.of(2024, 1, 2), bd("0.01"), bd("0")); p.mark(LocalDate.of(2024, 1, 3), bd("0.01"), bd("0"));
    assertNull(p.days.get(2).dailyReturn()); money("0", p.days.get(2).normalizedNav());
    assertTrue(p.metricWarnings.contains("DAILY_RETURN_ZERO_DENOMINATOR"));
  }
  @Test void insufficientDuplicateUnorderedAndNonpositiveBarsFailClosed() {
    assertEquals("INSUFFICIENT_DATA", assertThrows(BacktestException.class, () -> run(ma(), prices(100, 100, 100, 100))).code());
    for (List<DailyBar> bars : List.of(prices(100, 0, 100, 100, 100), prices(100, -1, 100, 100, 100),
        List.of(new DailyBar(LocalDate.of(2024, 1, 1), 100), new DailyBar(LocalDate.of(2024, 1, 1), 101)),
        List.of(new DailyBar(LocalDate.of(2024, 1, 2), 100), new DailyBar(LocalDate.of(2024, 1, 1), 101))))
      assertEquals("DATA_INTEGRITY_FAILED", assertThrows(BacktestException.class, () -> run(ma(), bars)).code());
    assertThrows(BacktestException.class, () -> run(ma(), Arrays.asList((DailyBar) null)));
    assertThrows(BacktestException.class, () -> run(ma(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new DailyBar(LocalDate.of(2024, 1, 1), Double.NaN));
  }
  @Test void extremeShortPeriodAnnualizationIsUnavailableAndNeverInfinityJson() {
    var dca = run(ma(), prices(1, 1, 1, 1, 1000000000)).get(1);
    assertNull(dca.annualizedReturn()); assertTrue(dca.metricWarnings().contains("ANNUALIZATION_NUMERIC_RANGE"));
    assertTrue(Double.isFinite(dca.timeWeightedReturn()));
    String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(dca);
    assertFalse(json.contains("Infinity")); assertFalse(json.contains("NaN")); assertTrue(json.contains("\"annualizedReturn\":null"));
  }
}
