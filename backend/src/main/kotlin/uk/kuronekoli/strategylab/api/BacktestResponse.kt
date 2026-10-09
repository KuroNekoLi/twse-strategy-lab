package uk.kuronekoli.strategylab.api

import java.math.BigDecimal

data class BacktestResponse(
  val symbol: String, val symbols: List<String>, val from: String, val to: String, val tradingDays: Int,
  val assets: List<AssetPeriod>, val dataSource: String, val assumptions: List<String>, val results: List<StrategyResult>,
  val requestedFrom: String, val requestedTo: String, val status: String, val releaseStatus: String,
  val limitations: List<Limitation>, val metadata: RunMetadata
) {
  data class AssetPeriod(val symbol: String, val from: String, val to: String, val tradingDays: Int,
    val requestedFrom: String, val requestedTo: String, val coverageStatus: String, val marketStatus: String)
  data class Point(val date: String, val value: BigDecimal)
  data class Limitation(val code: String, val message: String)
  data class StrategyResult(val key: String, val name: String, val endingValue: BigDecimal, val contributed: BigDecimal,
    val totalReturn: Double, val annualizedReturn: Double?, val maxDrawdown: Double, val series: List<Point>,
    val profit: BigDecimal, val timeWeightedReturn: Double?, val annualizationBasis: String,
    val trades: List<Trade>, val dailyEquity: List<DailyEquity>, val metricWarnings: List<String>,
    val annualizedRealizedVolatility: Double?)
  data class Trade(val signalDate: String?, val executionDate: String, val side: String, val status: String, val reason: String,
    val quantity: Long, val price: BigDecimal, val gross: BigDecimal, val fee: BigDecimal, val tax: BigDecimal,
    val cashAfter: BigDecimal, val positionAfter: Long, val averageCostAfter: BigDecimal)
  data class DailyEquity(val date: String, val equity: BigDecimal, val cash: BigDecimal, val shares: Long,
    val contributed: BigDecimal, val externalFlow: BigDecimal, val positionCost: BigDecimal,
    val normalizedNav: BigDecimal, val dailyReturn: Double?, val drawdown: Double)
  data class DatasetManifest(val symbol: String, val sha256: String, val rows: Int, val from: String, val to: String,
    val calendarCoverage: String, val corporateActions: String, val dividends: String)
  data class RunMetadata(val backtestId: String, val engineVersion: String, val strategyVersion: String,
    val executionModel: String, val executionModelVersion: String, val costModelVersion: String,
    val priceAdjustmentPolicy: String, val marketRulesVersion: String, val metricsVersion: String,
    val datasetHash: String, val reproducibilityStatus: String, val resolvedConfig: ResolvedConfig,
    val datasets: List<DatasetManifest>)
  data class ResolvedConfig(val symbols: List<String>, val from: String, val to: String, val effectiveTo: String,
    val strategy: String, val fastWindow: Int, val slowWindow: Int, val rsiWindow: Int, val rsiBuyThreshold: Double,
    val rsiSellThreshold: Double, val bollingerWindow: Int, val bollingerMultiplier: Double, val breakoutWindow: Int,
    val drawdownBuyPercent: Double, val profitSellPercent: Double, val initialCapital: BigDecimal,
    val monthlyContribution: BigDecimal, val commissionRate: BigDecimal, val sellTaxRate: BigDecimal,
    val useMarketTaxDefaults: Boolean, val appliedSellTaxRates: Map<String, BigDecimal>)
}
