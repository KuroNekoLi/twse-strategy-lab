package uk.kuronekoli.strategylab.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record BacktestResponse(String symbol, List<String> symbols, String from, String to, int tradingDays,
    List<AssetPeriod> assets, String dataSource, List<String> assumptions, List<StrategyResult> results,
    String requestedFrom, String requestedTo, String status, String releaseStatus,
    List<Limitation> limitations, RunMetadata metadata) {
  public record AssetPeriod(String symbol, String from, String to, int tradingDays,
      String requestedFrom, String requestedTo, String coverageStatus, String marketStatus) {}
  public record Point(String date, BigDecimal value) {}
  public record Limitation(String code, String message) {}
  public record StrategyResult(String key, String name, BigDecimal endingValue, BigDecimal contributed,
      double totalReturn, Double annualizedReturn, double maxDrawdown, List<Point> series,
      BigDecimal profit, Double timeWeightedReturn, String annualizationBasis,
      List<Trade> trades, List<DailyEquity> dailyEquity, List<String> metricWarnings,
      Double annualizedRealizedVolatility) {}
  public record Trade(String signalDate, String executionDate, String side, String status, String reason,
      long quantity, BigDecimal price, BigDecimal gross, BigDecimal fee, BigDecimal tax,
      BigDecimal cashAfter, long positionAfter, BigDecimal averageCostAfter) {}
  public record DailyEquity(String date, BigDecimal equity, BigDecimal cash, long shares,
      BigDecimal contributed, BigDecimal externalFlow, BigDecimal positionCost,
      BigDecimal normalizedNav, Double dailyReturn, double drawdown) {}
  public record DatasetManifest(String symbol, String sha256, int rows, String from, String to,
      String calendarCoverage, String corporateActions, String dividends) {}
  public record RunMetadata(String backtestId, String engineVersion, String strategyVersion,
      String executionModel, String executionModelVersion, String costModelVersion,
      String priceAdjustmentPolicy, String marketRulesVersion, String metricsVersion,
      String datasetHash, String reproducibilityStatus, ResolvedConfig resolvedConfig,
      List<DatasetManifest> datasets) {}
  public record ResolvedConfig(List<String> symbols, String from, String to, String effectiveTo,
      String strategy, int fastWindow, int slowWindow, int rsiWindow, double rsiBuyThreshold,
      double rsiSellThreshold, int bollingerWindow, double bollingerMultiplier, int breakoutWindow,
      double drawdownBuyPercent, double profitSellPercent, BigDecimal initialCapital,
      BigDecimal monthlyContribution, BigDecimal commissionRate, BigDecimal sellTaxRate,
      boolean useMarketTaxDefaults, Map<String, BigDecimal> appliedSellTaxRates) {}
}
