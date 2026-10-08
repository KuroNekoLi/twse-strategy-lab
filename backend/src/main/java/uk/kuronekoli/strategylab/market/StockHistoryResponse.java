package uk.kuronekoli.strategylab.market;

import java.util.List;

public record StockHistoryResponse(String symbol, String from, String to, String observedFrom, String observedTo,
    String fetchedAt, String source, String interval, String licensingStatus, String adjustmentPolicy,
    List<Bar> bars, List<String> limitations) {
  public record Bar(String date, String close) {}
}
