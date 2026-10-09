package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import uk.kuronekoli.strategylab.api.BacktestResponse.DailyEquity
import uk.kuronekoli.strategylab.api.BacktestResponse.Point
import uk.kuronekoli.strategylab.api.BacktestResponse.Trade

/** All money is cents HALF_UP; average unit cost is fee-inclusive, scale 8 HALF_UP. */
internal class AccountingPortfolio {
  var cash: BigDecimal = cents(BigDecimal.ZERO)
  var contributed: BigDecimal = cash
  var positionCost: BigDecimal = cash
  var previousEquity: BigDecimal? = null
  var nav: BigDecimal = BigDecimal.ONE
  private var peakNav: BigDecimal = BigDecimal.ONE
  var maxDrawdown: BigDecimal = BigDecimal.ZERO
  var shares: Long = 0
  val points = mutableListOf<Point>()
  val trades = mutableListOf<Trade>()
  val days = mutableListOf<DailyEquity>()
  val metricWarnings = mutableListOf<String>()

  fun averageCost(): BigDecimal = if (shares == 0L) BigDecimal.ZERO.setScale(8) else positionCost.divide(BigDecimal.valueOf(shares), 8, RoundingMode.HALF_UP)
  fun deposit(value: BigDecimal) { cash = cash.add(cents(value)); contributed = contributed.add(cents(value)) }
  private fun gross(price: BigDecimal, quantity: Long) = cents(price.multiply(BigDecimal.valueOf(quantity)))
  private fun expense(gross: BigDecimal, rate: BigDecimal) = cents(gross.multiply(rate))
  private fun purchaseCost(price: BigDecimal, quantity: Long, commission: BigDecimal): BigDecimal {
    val amount = gross(price, quantity); return amount.add(expense(amount, commission))
  }

  fun buy(signalDate: LocalDate?, executionDate: LocalDate, price: BigDecimal, commission: BigDecimal, reason: String) {
    // Find the maximum affordable whole-share quantity using the actual rounded charges.
    val bound = cash.add(BigDecimal("0.01")).divide(price, 0, RoundingMode.DOWN).add(BigDecimal.ONE)
    val upper = bound.min(BigDecimal.valueOf(Long.MAX_VALUE - shares)).longValueExact()
    var low = 0L; var high = upper
    while (low < high) {
      val middle = low + (high - low) / 2 + 1
      if (purchaseCost(price, middle, commission).compareTo(cash) <= 0) low = middle else high = middle - 1
    }
    val quantity = low
    val amount = gross(price, quantity); val fee = expense(amount, commission)
    // Cent rounding can otherwise turn a positive number of sub-cent shares into a free fill.
    if (quantity > 0 && amount.signum() > 0) {
      val total = amount.add(fee); cash = cash.subtract(total); positionCost = positionCost.add(total); shares += quantity
    }
    val filled = quantity > 0 && amount.signum() > 0
    trades.add(Trade(signalDate?.toString(), executionDate.toString(), "BUY",
      if (filled) "FILLED" else "SKIPPED_INSUFFICIENT_CASH", reason,
      if (filled) quantity else 0, price, if (filled) amount else cents(BigDecimal.ZERO),
      if (filled) fee else cents(BigDecimal.ZERO), cents(BigDecimal.ZERO), cash, shares, averageCost()))
  }

  fun sell(signalDate: LocalDate, executionDate: LocalDate, price: BigDecimal, commission: BigDecimal, tax: BigDecimal, reason: String) {
    val quantity = shares
    val amount = gross(price, quantity); val fee = expense(amount, commission); val taxAmount = expense(amount, tax)
    cash = cash.add(amount.subtract(fee).subtract(taxAmount)); shares = 0; positionCost = cents(BigDecimal.ZERO)
    trades.add(Trade(signalDate.toString(), executionDate.toString(), "SELL", "FILLED", reason,
      quantity, price, amount, fee, taxAmount, cash, shares, averageCost()))
  }

  fun mark(date: LocalDate, price: BigDecimal, externalFlow: BigDecimal) {
    val equity = cash.add(gross(price, shares))
    val denominator = previousEquity?.add(externalFlow) ?: externalFlow
    if (denominator.signum() < 0) throw IllegalStateException("Negative TWR denominator")
    val factor: BigDecimal
    val dailyReturn: Double?
    if (denominator.signum() == 0) {
      if (equity.signum() != 0) throw IllegalStateException("Unexplained equity after zero denominator")
      factor = BigDecimal.ONE; dailyReturn = null
      if (!metricWarnings.contains("DAILY_RETURN_ZERO_DENOMINATOR")) metricWarnings.add("DAILY_RETURN_ZERO_DENOMINATOR")
    } else {
      factor = equity.divide(denominator, MC)
      dailyReturn = factor.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).toDouble()
    }
    nav = nav.multiply(factor, MC); peakNav = peakNav.max(nav)
    val drawdown = BigDecimal.ONE.subtract(nav.divide(peakNav, MC)); maxDrawdown = maxDrawdown.max(drawdown)
    days.add(DailyEquity(date.toString(), equity, cash, shares, contributed, externalFlow,
      positionCost, nav.setScale(12, RoundingMode.HALF_UP), dailyReturn,
      drawdown.multiply(BigDecimal.valueOf(100)).toDouble()))
    points.add(Point(date.toString(), equity)); previousEquity = equity
    if (cash.signum() < 0 || shares < 0 || positionCost.signum() < 0 || (shares == 0L && positionCost.signum() != 0))
      throw IllegalStateException("Accounting invariant failed")
  }

  companion object {
    private val MC = MathContext.DECIMAL128
    fun cents(value: BigDecimal) = value.setScale(2, RoundingMode.HALF_UP)
  }
}
