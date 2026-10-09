package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.api.BacktestResponse.StrategyResult
import uk.kuronekoli.strategylab.api.BacktestResponse.Trade
import uk.kuronekoli.strategylab.market.DailyBar

class BacktestEngineGoldenTest {
  private fun bd(n: String) = BigDecimal(n)

  internal fun request(strategy: String, capital: String, monthly: String, fee: String, tax: String) = BacktestRequest(
    "2330", null, "2024-01-01", "2024-12-31", strategy, 2, 3, 2, 30.0, 55.0,
    3, 0.5, 2, 20.0, 20.0, bd(monthly), bd(capital), bd(fee), bd(tax), false
  )

  internal fun prices(vararg prices: Double): List<DailyBar> = prices(LocalDate.of(2024, 1, 1), *prices)

  private fun prices(start: LocalDate, vararg prices: Double): List<DailyBar> = prices.mapIndexed { index, value -> DailyBar(start.plusDays(index.toLong()), value) }

  private fun run(request: BacktestRequest, bars: List<DailyBar>) = BacktestEngine.run(request, "2330", bars, request.sellTaxRate!!)
  private fun money(expected: String, actual: BigDecimal) = assertEquals(0, bd(expected).compareTo(actual), "$expected vs $actual")
  private fun ma() = request("ma-crossover", "1000", "0", "0", "0")

  @Test fun risingPricesDoNotInventCrossingAndDcaGrowsFortyPercent() {
    val results = run(ma(), prices(100.0, 110.0, 120.0, 130.0, 140.0))
    money("1000", results[0].endingValue); assertTrue(results[0].trades.isEmpty())
    val dca = results[1]; money("1400", dca.endingValue); assertEquals(40.0, dca.timeWeightedReturn!!, 1e-10); assertEquals(0.0, dca.maxDrawdown, 1e-10)
  }

  @Test fun annualizedRealizedVolatilityUsesSampleDeviationOfDailyPercentReturns() {
    val result = run(request("ma-crossover", "10000", "0", "0", "0"), prices(100.0, 110.0, 99.0, 108.9, 108.9))[2]
    assertEquals(kotlin.math.sqrt(70.0 * 252), result.annualizedRealizedVolatility!!, 1e-10)
    assertEquals(0.0, BacktestEngine.annualizedRealizedVolatility(listOf(0.0, 0.0))!!, 0.0)
  }

  @Test fun realizedVolatilityIgnoresUnavailableReturnsAndRequiresTwoValidObservations() {
    assertNull(BacktestEngine.annualizedRealizedVolatility(listOf(null, 10.0)))
    assertNull(BacktestEngine.annualizedRealizedVolatility(listOf(null, 10.0, Double.NaN)))
    assertEquals(kotlin.math.sqrt(50400.0), BacktestEngine.annualizedRealizedVolatility(listOf(null, 10.0, -10.0))!!, 1e-10)
  }

  @Test fun buyAndHoldInvestsInitialCapitalOnceAndKeepsLaterMonthlyFlowsAsCash() {
    val result = run(request("ma-crossover", "1000", "500", "0.01", "0"), listOf(
      DailyBar(LocalDate.of(2024, 1, 31), 100.0), DailyBar(LocalDate.of(2024, 2, 1), 200.0),
      DailyBar(LocalDate.of(2024, 2, 2), 200.0), DailyBar(LocalDate.of(2024, 3, 1), 50.0), DailyBar(LocalDate.of(2024, 3, 2), 50.0)
    ))[2]
    assertEquals("2330-buy-and-hold", result.key); assertEquals("2330 · 買進持有（後續月投入保留現金）", result.name); assertEquals(1, result.trades.size)
    val initial = result.trades[0]
    assertEquals("BUY_AND_HOLD_INITIAL_CAPITAL_FIRST_OBSERVED_CLOSE_V1", initial.reason); assertEquals(9, initial.quantity)
    money("900", initial.gross); money("9", initial.fee); money("91", initial.cashAfter)
    assertEquals(9, result.dailyEquity[0].shares); money("91", result.dailyEquity[0].cash)
    assertEquals(9, result.dailyEquity[1].shares); money("591", result.dailyEquity[1].cash)
    assertEquals(9, result.dailyEquity[3].shares); money("1091", result.dailyEquity[3].cash)
    money("1541", result.endingValue); money("2000", result.contributed); money("-459", result.profit)
    assertEquals(500.0, result.dailyEquity[1].externalFlow.toDouble()); assertEquals(500.0, result.dailyEquity[3].externalFlow.toDouble())
  }

  @Test fun fallingAndFlatPricesHaveHandwrittenReturns() {
    val falling = run(ma(), prices(100.0, 90.0, 80.0, 70.0, 60.0))[1]
    money("600", falling.endingValue); assertEquals(-40.0, falling.timeWeightedReturn!!, 1e-10); assertEquals(40.0, falling.maxDrawdown, 1e-10)
    val flat = run(ma(), prices(100.0, 100.0, 100.0, 100.0, 100.0))[1]
    money("1000", flat.endingValue); assertEquals(0.0, flat.timeWeightedReturn!!, 1e-10); assertEquals(0.0, flat.annualizedReturn!!, 1e-10)
  }

  @Test fun goldenAndDeathCrossExecuteOnlyAtNextObservedClose() {
    val active = run(ma(), prices(100.0, 100.0, 100.0, 120.0, 110.0, 80.0, 90.0, 100.0))[0]
    assertEquals(2, active.trades.size); val buy = active.trades[0]; val sell = active.trades[1]
    assertEquals("2024-01-04", buy.signalDate); assertEquals("2024-01-05", buy.executionDate); assertEquals(9, buy.quantity)
    money("110", buy.price); money("10", buy.cashAfter); assertEquals("2024-01-06", sell.signalDate); assertEquals("2024-01-07", sell.executionDate)
    money("90", sell.price); money("820", active.endingValue); assertEquals(-18.0, active.timeWeightedReturn!!, 1e-10)
    assertEquals(0, active.dailyEquity[7].shares)
  }

  @Test fun feesTaxResidualCashAndFeeInclusiveCostMatchIndependentArithmetic() {
    val active = run(request("ma-crossover", "1000", "0", "0.01", "0.003"), prices(100.0, 100.0, 100.0, 120.0, 100.0, 80.0, 110.0))[0]
    val buy = active.trades[0]; val sell = active.trades[1]
    assertEquals(9, buy.quantity); money("900", buy.gross); money("9", buy.fee); money("91", buy.cashAfter); money("101", buy.averageCostAfter)
    money("990", sell.gross); money("9.90", sell.fee); money("2.97", sell.tax); money("1068.13", sell.cashAfter)
    money("1068.13", active.endingValue); money("68.13", active.profit); assertEquals(6.813, active.timeWeightedReturn!!, 1e-10)
  }

  @Test fun feeRoundingUsesCentsHalfUpAndFirstDayCostIsTwrLoss() {
    val dca = run(request("ma-crossover", "1000", "0", "0.01", "0"), prices(100.0, 100.0, 100.0, 100.0, 100.0))[1]
    money("991", dca.endingValue); assertEquals(-0.9, dca.timeWeightedReturn!!, 1e-10); assertEquals(0.9, dca.maxDrawdown, 1e-10)
    val p = AccountingPortfolio(); p.deposit(bd("10.01")); p.buy(null, LocalDate.of(2024, 1, 1), bd("10"), bd("0.0005"), "SYNTHETIC")
    assertEquals(1, p.shares); money("0.01", p.trades[0].fee); money("0", p.cash)
    p.sell(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), bd("10"), bd("0.0005"), bd("0.0005"), "SYNTHETIC")
    money("9.98", p.cash); money("0.01", p.trades[1].tax)
  }

  @Test fun subCentGrossCannotCreateFreeSharesAfterCashIsExhausted() {
    val p = AccountingPortfolio(); p.deposit(bd("0.01")); p.buy(null, LocalDate.of(2024, 1, 31), bd("0.01"), bd("0"), "SYNTHETIC")
    assertEquals(1, p.shares); money("0", p.cash); p.mark(LocalDate.of(2024, 1, 31), bd("0.01"), bd("0.01"))
    p.buy(null, LocalDate.of(2024, 2, 1), bd("0.0001"), bd("0"), "SYNTHETIC")
    val skipped = p.trades[1]; assertEquals("SKIPPED_INSUFFICIENT_CASH", skipped.status); assertEquals(0, skipped.quantity)
    money("0", skipped.gross); money("0", p.cash); assertEquals(1, p.shares); p.mark(LocalDate.of(2024, 2, 1), bd("1"), bd("0")); money("1", p.days[1].equity)
  }

  @Test fun roundedAffordabilityCanBuyAWholeShareWithoutTheoreticalFractionalFee() {
    val p = AccountingPortfolio(); p.deposit(bd("10.00")); p.buy(null, LocalDate.of(2024, 1, 1), bd("10"), bd("0.0001"), "SYNTHETIC")
    assertEquals(1, p.shares); money("0", p.cash); money("0", p.trades[0].fee)
  }

  @Test fun cashShortageCreatesAnExplicitSkippedIntentAndNoFractionalShares() {
    val results = run(request("ma-crossover", "50", "0", "0", "0"), prices(100.0, 100.0, 100.0, 120.0, 100.0))
    for (r in results) { money("50", r.endingValue); assertEquals(0, r.trades[0].quantity); assertEquals("SKIPPED_INSUFFICIENT_CASH", r.trades[0].status); assertEquals(0, r.dailyEquity[4].shares) }
  }

  @Test fun monthlyDepositCannotHideTenPercentDrawdown() {
    val dca = run(request("ma-crossover", "1000", "1000", "0", "0"), prices(LocalDate.of(2024, 1, 27), 100.0, 100.0, 100.0, 90.0, 90.0, 90.0, 90.0))[1]
    money("2000", dca.contributed); money("1900", dca.endingValue); assertEquals(-5.0, dca.totalReturn, 1e-10)
    assertEquals(-10.0, dca.timeWeightedReturn!!, 1e-10); assertEquals(10.0, dca.maxDrawdown, 1e-10)
    val depositDay = dca.dailyEquity[5]; money("1000", depositDay.externalFlow); money("1900", depositDay.equity)
    money("0.9", depositDay.normalizedNav); assertEquals(0.0, depositDay.dailyReturn!!, 1e-10)
    val annual = (Math.pow(0.9, 365.2425 / 6) - 1) * 100; assertEquals(annual, dca.annualizedReturn!!, 1e-10)
  }

  @Test fun rsiSimpleRollingLevelHasKnownBuyAndSellDates() {
    val active = run(request("rsi-reversion", "1000", "0", "0", "0"), prices(100.0, 90.0, 80.0, 70.0, 100.0, 110.0))[0]
    assertEquals(2, active.trades.size); assertEquals("2024-01-03", active.trades[0].signalDate); assertEquals("2024-01-04", active.trades[0].executionDate)
    assertEquals(14, active.trades[0].quantity); assertEquals("2024-01-05", active.trades[1].signalDate); money("1560", active.endingValue)
  }

  @Test fun bollingerUsesPopulationDeviationAndLevelThreshold() {
    val active = run(request("bollinger-reversion", "1000", "0", "0", "0"), prices(100.0, 90.0, 80.0, 90.0, 100.0))[0]
    assertEquals(2, active.trades.size); assertEquals("2024-01-03", active.trades[0].signalDate); assertEquals(11, active.trades[0].quantity)
    assertEquals("2024-01-05", active.trades[1].executionDate); money("1110", active.endingValue)
  }

  @Test fun breakoutHighExcludesSignalDayAndExitUsesItsMean() {
    val active = run(request("breakout", "1000", "0", "0", "0"), prices(100.0, 100.0, 120.0, 130.0, 80.0, 90.0))[0]
    assertEquals(2, active.trades.size); assertEquals("2024-01-03", active.trades[0].signalDate); assertEquals(7, active.trades[0].quantity)
    assertEquals("2024-01-06", active.trades[1].executionDate); money("720", active.endingValue)
  }

  @Test fun drawdownPrior252ExcludesSignalDayAndRetainsOldestPriorHigh() {
    val values = DoubleArray(256) { 100.0 }; values[0] = 200.0; values[252] = 150.0; values[253] = 100.0; values[254] = 130.0; values[255] = 110.0
    val bars = prices(*values); val active = run(request("drawdown-entry", "1000", "0", "0", "0"), bars)[0]
    assertEquals(2, active.trades.size); assertEquals(bars[252].date.toString(), active.trades[0].signalDate)
    assertEquals(bars[253].date.toString(), active.trades[0].executionDate); assertEquals(10, active.trades[0].quantity)
    assertEquals(bars[254].date.toString(), active.trades[1].signalDate); money("1100", active.endingValue)
  }

  @Test fun identicalCapturedBarsReplayExactlyAndFutureSuffixCannotChangePast() {
    val bars = prices(100.0, 100.0, 100.0, 120.0, 110.0, 80.0, 90.0, 100.0)
    assertEquals(run(ma(), bars), run(ma(), bars.toList()))
    val extended = bars.toMutableList().apply { add(DailyBar(LocalDate.of(2024, 1, 9), 10000.0)); add(DailyBar(LocalDate.of(2024, 1, 10), 1.0)) }
    for (strategyIndex in 0..1) {
      val before = run(ma(), bars)[strategyIndex]; val after = run(ma(), extended)[strategyIndex]
      assertEquals(before.dailyEquity, after.dailyEquity.subList(0, bars.size)); assertEquals(before.series, after.series.subList(0, bars.size))
      assertEquals(before.trades, after.trades.filter { LocalDate.parse(it.executionDate).isBefore(LocalDate.of(2024, 1, 9)) })
    }
  }

  @Test fun ledgerCanBeRebuiltFromIndependentCashAndPositionEquations() {
    val bars = prices(LocalDate.of(2024, 1, 27), 100.0, 100.0, 100.0, 120.0, 100.0, 80.0, 110.0, 100.0, 120.0)
    for (result in run(request("ma-crossover", "1000", "100", "0.01", "0.003"), bars)) {
      var cash = bd("0"); var contributed = bd("0"); var basis = bd("0"); var shares = 0L
      for (i in bars.indices) {
        val day = result.dailyEquity[i]; cash = cash.add(day.externalFlow); contributed = contributed.add(day.externalFlow)
        for (trade in result.trades.filter { it.executionDate == day.date }) {
          if (trade.side == "BUY") { cash = cash.subtract(trade.gross).subtract(trade.fee); shares += trade.quantity; basis = basis.add(trade.gross).add(trade.fee) }
          else { cash = cash.add(trade.gross).subtract(trade.fee).subtract(trade.tax); shares -= trade.quantity; basis = bd("0") }
          assertEquals(0, cash.compareTo(trade.cashAfter)); assertEquals(shares, trade.positionAfter)
        }
        assertEquals(0, cash.compareTo(day.cash)); assertEquals(shares, day.shares); assertTrue(cash.signum() >= 0)
        assertEquals(0, contributed.compareTo(day.contributed)); assertEquals(0, basis.compareTo(day.positionCost))
        val equity = cash.add(bars[i].close.multiply(BigDecimal.valueOf(shares)).setScale(2, RoundingMode.HALF_UP))
        assertEquals(0, equity.compareTo(day.equity))
      }
      assertEquals(0, result.endingValue.subtract(result.contributed).compareTo(result.profit))
    }
  }

  @Test fun futureSuffixInvarianceAppliesToEveryVersionedStrategy() {
    val longDrawdown = DoubleArray(256) { 100.0 }; longDrawdown[0] = 200.0; longDrawdown[252] = 150.0; longDrawdown[253] = 100.0; longDrawdown[254] = 130.0; longDrawdown[255] = 110.0
    val fixtures = mapOf("ma-crossover" to prices(100.0, 100.0, 100.0, 120.0, 110.0, 80.0, 90.0, 100.0),
      "rsi-reversion" to prices(100.0, 90.0, 80.0, 70.0, 100.0, 110.0), "bollinger-reversion" to prices(100.0, 90.0, 80.0, 90.0, 100.0),
      "breakout" to prices(100.0, 100.0, 120.0, 130.0, 80.0, 90.0), "drawdown-entry" to prices(*longDrawdown))
    for ((strategy, prefix) in fixtures) {
      val r = request(strategy, "1000", "0", "0", "0"); val firstFuture = prefix.last().date.plusDays(1)
      val extended = prefix.toMutableList().apply { add(DailyBar(firstFuture, 10000.0)); add(DailyBar(firstFuture.plusDays(1), 1.0)) }
      for (index in 0..1) {
        val before = run(r, prefix)[index]; val after = run(r, extended)[index]
        assertEquals(before.dailyEquity, after.dailyEquity.subList(0, prefix.size), strategy)
        assertEquals(before.trades, after.trades.filter { LocalDate.parse(it.executionDate).isBefore(firstFuture) }, strategy)
      }
    }
  }

  @Test fun zeroEquityFollowingRoundedSellCostsMakesSubsequentDailyReturnUnavailable() {
    val p = AccountingPortfolio(); p.deposit(bd("0.01")); p.buy(null, LocalDate.of(2024, 1, 1), bd("0.01"), bd("0"), "SYNTHETIC")
    p.mark(LocalDate.of(2024, 1, 1), bd("0.01"), bd("0.01")); p.sell(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2), bd("0.01"), bd("0.99"), bd("0"), "SYNTHETIC")
    p.mark(LocalDate.of(2024, 1, 2), bd("0.01"), bd("0")); p.mark(LocalDate.of(2024, 1, 3), bd("0.01"), bd("0"))
    assertNull(p.days[2].dailyReturn); money("0", p.days[2].normalizedNav); assertTrue(p.metricWarnings.contains("DAILY_RETURN_ZERO_DENOMINATOR"))
  }

  @Test fun insufficientDuplicateUnorderedAndNonpositiveBarsFailClosed() {
    assertEquals("INSUFFICIENT_DATA", assertThrows(BacktestException::class.java) { run(ma(), prices(100.0, 100.0, 100.0, 100.0)) }.code)
    val invalid = listOf(prices(100.0, 0.0, 100.0, 100.0, 100.0), prices(100.0, -1.0, 100.0, 100.0, 100.0),
      listOf(DailyBar(LocalDate.of(2024, 1, 1), 100.0), DailyBar(LocalDate.of(2024, 1, 1), 101.0)),
      listOf(DailyBar(LocalDate.of(2024, 1, 2), 100.0), DailyBar(LocalDate.of(2024, 1, 1), 101.0)))
    for (bars in invalid) assertEquals("DATA_INTEGRITY_FAILED", assertThrows(BacktestException::class.java) { run(ma(), bars) }.code)
    assertThrows(BacktestException::class.java) { run(ma(), listOf(DailyBar(LocalDate.of(2024, 1, 1), 100.0))) }
    assertThrows(BacktestException::class.java) { BacktestValidation.bars(emptyList()) }
    assertThrows(IllegalArgumentException::class.java) { DailyBar(LocalDate.of(2024, 1, 1), Double.NaN) }
  }

  @Test fun extremeShortPeriodAnnualizationIsUnavailableAndNeverInfinityJson() {
    val dca = run(ma(), prices(1.0, 1.0, 1.0, 1.0, 1_000_000_000.0))[1]
    assertNull(dca.annualizedReturn); assertTrue(dca.metricWarnings.contains("ANNUALIZATION_NUMERIC_RANGE")); assertTrue(dca.timeWeightedReturn!!.isFinite())
    val json = JsonMapper.builder().build().writeValueAsString(dca)
    assertFalse(json.contains("Infinity")); assertFalse(json.contains("NaN")); assertTrue(json.contains("\"annualizedReturn\":null"))
  }
}
