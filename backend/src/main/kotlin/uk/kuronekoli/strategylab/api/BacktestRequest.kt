package uk.kuronekoli.strategylab.api

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

/** Nullable request fields preserve the API's validation-driven missing-value behavior. */
data class BacktestRequest(
  @field:NotBlank val symbol: String?,
  val symbols: List<String?>?,
  @field:NotBlank val from: String?,
  @field:NotBlank val to: String?,
  val strategy: String?,
  @field:NotNull @field:Positive val fastWindow: Int?,
  @field:NotNull @field:Positive val slowWindow: Int?,
  val rsiWindow: Int?,
  val rsiBuyThreshold: Double?,
  val rsiSellThreshold: Double?,
  val bollingerWindow: Int?,
  val bollingerMultiplier: Double?,
  val breakoutWindow: Int?,
  val drawdownBuyPercent: Double?,
  val profitSellPercent: Double?,
  @field:NotNull @field:DecimalMin("0") val monthlyContribution: BigDecimal?,
  @field:NotNull @field:DecimalMin("0.01") val initialCapital: BigDecimal?,
  @field:NotNull @field:DecimalMin("0") val commissionRate: BigDecimal?,
  @field:NotNull @field:DecimalMin("0") val sellTaxRate: BigDecimal?,
  val useMarketTaxDefaults: Boolean?
) {
  fun strategyOrDefault(): String = if (strategy.isNullOrBlank()) "ma-crossover" else strategy
  fun value(value: Double?, fallback: Double): Double = value ?: fallback
  fun value(value: Int?, fallback: Int): Int = value ?: fallback
  fun marketTaxDefaults(): Boolean = useMarketTaxDefaults ?: true
}
