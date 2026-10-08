package uk.kuronekoli.strategylab.market;

import java.time.LocalDate;
import java.math.BigDecimal;

/** Captured close only: no OHLC, dividend or corporate-action data is implied. */
public record DailyBar(LocalDate date, BigDecimal close) {
  public DailyBar(LocalDate date, double close) { this(date, decimal(close)); }
  private static BigDecimal decimal(double value) {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("收盤價必須為有限數值。");
    return BigDecimal.valueOf(value);
  }
}
