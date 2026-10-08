package uk.kuronekoli.strategylab.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public record BacktestRequest(
    @NotBlank String symbol,
    List<String> symbols,
    @NotBlank String from,
    @NotBlank String to,
    String strategy,
    @NotNull @Positive Integer fastWindow,
    @NotNull @Positive Integer slowWindow,
    Integer rsiWindow,
    Double rsiBuyThreshold,
    Double rsiSellThreshold,
    Integer bollingerWindow,
    Double bollingerMultiplier,
    Integer breakoutWindow,
    Double drawdownBuyPercent,
    Double profitSellPercent,
    @NotNull @DecimalMin("0") BigDecimal monthlyContribution,
    @NotNull @DecimalMin(value = "0.01") BigDecimal initialCapital,
    @NotNull @DecimalMin("0") BigDecimal commissionRate,
    @NotNull @DecimalMin("0") BigDecimal sellTaxRate,
    Boolean useMarketTaxDefaults) {
  public String strategyOrDefault() { return strategy == null || strategy.isBlank() ? "ma-crossover" : strategy; }
  public double value(Double value, double fallback) { return value == null ? fallback : value; }
  public int value(Integer value, int fallback) { return value == null ? fallback : value; }
  public boolean marketTaxDefaults() { return useMarketTaxDefaults == null || useMarketTaxDefaults; }
}
