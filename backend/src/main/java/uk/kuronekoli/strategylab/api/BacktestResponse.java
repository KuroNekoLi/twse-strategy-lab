package uk.kuronekoli.strategylab.api;

import java.util.List;

public record BacktestResponse(String symbol, List<String> symbols, String from, String to, int tradingDays,
    List<AssetPeriod> assets, String dataSource, List<String> assumptions, List<StrategyResult> results) {
  public record AssetPeriod(String symbol, String from, String to, int tradingDays) {}
  public record Point(String date, double value) {}
  public record StrategyResult(String key, String name, double endingValue, double contributed,
      double totalReturn, double annualizedReturn, double maxDrawdown, List<Point> series) {}
}
