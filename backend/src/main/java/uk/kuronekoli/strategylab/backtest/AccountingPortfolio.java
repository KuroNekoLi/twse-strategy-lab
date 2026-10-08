package uk.kuronekoli.strategylab.backtest;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import uk.kuronekoli.strategylab.api.BacktestResponse.DailyEquity;
import uk.kuronekoli.strategylab.api.BacktestResponse.Point;
import uk.kuronekoli.strategylab.api.BacktestResponse.Trade;

/** All money is cents HALF_UP; average unit cost is fee-inclusive, scale 8 HALF_UP. */
final class AccountingPortfolio {
  private static final MathContext MC = MathContext.DECIMAL128;
  BigDecimal cash = cents(BigDecimal.ZERO), contributed = cash, positionCost = cash, previousEquity;
  BigDecimal nav = BigDecimal.ONE, peakNav = BigDecimal.ONE, maxDrawdown = BigDecimal.ZERO;
  long shares;
  final List<Point> points = new ArrayList<>();
  final List<Trade> trades = new ArrayList<>();
  final List<DailyEquity> days = new ArrayList<>();
  final List<String> metricWarnings = new ArrayList<>();
  static BigDecimal cents(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
  BigDecimal averageCost() { return shares == 0 ? BigDecimal.ZERO.setScale(8) : positionCost.divide(BigDecimal.valueOf(shares), 8, RoundingMode.HALF_UP); }
  void deposit(BigDecimal value) { cash = cash.add(cents(value)); contributed = contributed.add(cents(value)); }
  private BigDecimal gross(BigDecimal price, long quantity) { return cents(price.multiply(BigDecimal.valueOf(quantity))); }
  private BigDecimal expense(BigDecimal gross, BigDecimal rate) { return cents(gross.multiply(rate)); }
  private BigDecimal purchaseCost(BigDecimal price, long quantity, BigDecimal commission) {
    BigDecimal amount = gross(price, quantity); return amount.add(expense(amount, commission));
  }
  void buy(LocalDate signalDate, LocalDate executionDate, BigDecimal price, BigDecimal commission, String reason) {
    // Find the maximum affordable whole-share quantity using the actual rounded charges.
    BigDecimal bound = cash.add(new BigDecimal("0.01")).divide(price, 0, RoundingMode.DOWN).add(BigDecimal.ONE);
    long upper = bound.min(BigDecimal.valueOf(Long.MAX_VALUE - shares)).longValueExact();
    long low = 0, high = upper;
    while (low < high) {
      long middle = low + (high - low) / 2 + 1;
      if (purchaseCost(price, middle, commission).compareTo(cash) <= 0) low = middle; else high = middle - 1;
    }
    long quantity = low;
    BigDecimal amount = gross(price, quantity), fee = expense(amount, commission);
    // Cent rounding can otherwise turn a positive number of sub-cent shares into a free fill.
    if (quantity > 0 && amount.signum() > 0) {
      BigDecimal total = amount.add(fee); cash = cash.subtract(total); positionCost = positionCost.add(total); shares += quantity;
    }
    trades.add(new Trade(signalDate == null ? null : signalDate.toString(), executionDate.toString(), "BUY",
        quantity > 0 && amount.signum() > 0 ? "FILLED" : "SKIPPED_INSUFFICIENT_CASH", reason,
        quantity > 0 && amount.signum() > 0 ? quantity : 0, price,
        quantity > 0 && amount.signum() > 0 ? amount : cents(BigDecimal.ZERO),
        quantity > 0 && amount.signum() > 0 ? fee : cents(BigDecimal.ZERO), cents(BigDecimal.ZERO), cash, shares, averageCost()));
  }
  void sell(LocalDate signalDate, LocalDate executionDate, BigDecimal price, BigDecimal commission, BigDecimal tax, String reason) {
    long quantity = shares;
    BigDecimal amount = gross(price, quantity), fee = expense(amount, commission), taxAmount = expense(amount, tax);
    cash = cash.add(amount.subtract(fee).subtract(taxAmount)); shares = 0; positionCost = cents(BigDecimal.ZERO);
    trades.add(new Trade(signalDate.toString(), executionDate.toString(), "SELL", "FILLED", reason,
        quantity, price, amount, fee, taxAmount, cash, shares, averageCost()));
  }
  void mark(LocalDate date, BigDecimal price, BigDecimal externalFlow) {
    BigDecimal equity = cash.add(gross(price, shares));
    BigDecimal denominator = previousEquity == null ? externalFlow : previousEquity.add(externalFlow);
    if (denominator.signum() < 0) throw new IllegalStateException("Negative TWR denominator");
    BigDecimal factor;
    Double dailyReturn;
    if (denominator.signum() == 0) {
      if (equity.signum() != 0) throw new IllegalStateException("Unexplained equity after zero denominator");
      factor = BigDecimal.ONE; dailyReturn = null;
      if (!metricWarnings.contains("DAILY_RETURN_ZERO_DENOMINATOR")) metricWarnings.add("DAILY_RETURN_ZERO_DENOMINATOR");
    } else {
      factor = equity.divide(denominator, MC);
      dailyReturn = factor.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).doubleValue();
    }
    nav = nav.multiply(factor, MC); peakNav = peakNav.max(nav);
    BigDecimal drawdown = BigDecimal.ONE.subtract(nav.divide(peakNav, MC)); maxDrawdown = maxDrawdown.max(drawdown);
    days.add(new DailyEquity(date.toString(), equity, cash, shares, contributed, externalFlow,
        positionCost, nav.setScale(12, RoundingMode.HALF_UP), dailyReturn,
        drawdown.multiply(BigDecimal.valueOf(100)).doubleValue()));
    points.add(new Point(date.toString(), equity)); previousEquity = equity;
    if (cash.signum() < 0 || shares < 0 || positionCost.signum() < 0 || (shares == 0 && positionCost.signum() != 0)) throw new IllegalStateException("Accounting invariant failed");
  }
}
