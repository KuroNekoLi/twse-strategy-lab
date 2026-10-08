package uk.kuronekoli.strategylab.backtest;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.market.TwseMarketDataClient;

class BacktestServiceTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-20T04:00:00Z"), ZoneId.of("Asia/Taipei"));
  private static class CapturedClient extends TwseMarketDataClient {
    private final List<DailyBar> bars;
    YearMonth requestedFirst, requestedLast;
    CapturedClient(List<DailyBar> bars) { super(JsonMapper.builder().build(), "https://example.invalid/STOCK_DAY"); this.bars = bars; }
    @Override public List<DailyBar> load(String symbol, YearMonth first, YearMonth last) { requestedFirst = first; requestedLast = last; return bars; }
  }
  @Test void coverageLimitationsAndResolvedFingerprintAreExplicitAndDeterministic() {
    var client = new CapturedClient(BacktestEngineGoldenTest.prices(100, 100, 100, 120, 110));
    var service = new BacktestService(client, CLOCK);
    var request = BacktestEngineGoldenTest.request("ma-crossover", "1000", "0", "0.001425", "0.003");
    var report = service.run(request);
    assertEquals("2024-12-31", report.requestedTo()); assertEquals("2024-01-05", report.to()); assertEquals("2024-01-20", report.metadata().resolvedConfig().effectiveTo());
    assertEquals(YearMonth.of(2024, 1), client.requestedLast);
    assertEquals("LIMITED_RESEARCH", report.status()); assertEquals("BLOCKED", report.releaseStatus());
    assertEquals("RAW_CLOSE_UNADJUSTED_V1", report.metadata().priceAdjustmentPolicy()); assertEquals("NEXT_CLOSE_PROXY", report.metadata().executionModel());
    assertEquals("IDENTIFIED_NOT_ARCHIVED", report.metadata().reproducibilityStatus());
    assertEquals("UNKNOWN", report.assets().get(0).marketStatus()); assertEquals("UNVERIFIED_CALENDAR", report.assets().get(0).coverageStatus());
    var codes = report.limitations().stream().map(l -> l.code()).toList();
    for (String expected : List.of("DIVIDENDS_UNSUPPORTED", "CORPORATE_ACTIONS_UNSUPPORTED", "CALENDAR_STATUS_UNKNOWN", "LICENSING_UNVERIFIED", "FUTURE_END_CLAMPED", "COVERAGE_DIFFERS_FROM_REQUEST", "SNAPSHOT_NOT_ARCHIVED")) assertTrue(codes.contains(expected), expected);
    assertEquals(report, service.run(request)); assertEquals(64, report.metadata().datasetHash().length()); assertTrue(report.metadata().backtestId().startsWith("bt_"));
  }
  @Test void equivalentDecimalRepresentationHasSameDatasetFingerprintButPriceChangeDoesNot() {
    var first = BacktestEngineGoldenTest.prices(100, 100, 100, 120, 110);
    var scaled = first.stream().map(b -> new DailyBar(b.date(), b.close().setScale(8))).toList();
    assertEquals(BacktestFingerprint.dataset("2330", first), BacktestFingerprint.dataset("2330", scaled));
    assertNotEquals(BacktestFingerprint.dataset("2330", first), BacktestFingerprint.dataset("2330", BacktestEngineGoldenTest.prices(100, 100, 100, 120, 111)));
  }
  @Test void parameterAndDatasetChangesChangeRunIdentity() {
    var base = new BacktestService(new CapturedClient(BacktestEngineGoldenTest.prices(100, 100, 100, 120, 110)), CLOCK);
    var changedData = new BacktestService(new CapturedClient(BacktestEngineGoldenTest.prices(100, 100, 100, 120, 111)), CLOCK);
    var request = BacktestEngineGoldenTest.request("ma-crossover", "1000", "0", "0", "0");
    String id = base.run(request).metadata().backtestId();
    assertNotEquals(id, changedData.run(request).metadata().backtestId());
    assertNotEquals(id, base.run(BacktestEngineGoldenTest.request("ma-crossover", "1001", "0", "0", "0")).metadata().backtestId());
  }
  @Test void invalidNullNegativeNonfiniteAndExcessiveInputsHaveStableCodes() {
    var service = new BacktestService(new CapturedClient(BacktestEngineGoldenTest.prices(100, 100, 100, 120, 110)), CLOCK);
    assertEquals("INVALID_INPUT", assertThrows(BacktestException.class, () -> service.run(null)).code());
    for (var request : List.of(BacktestEngineGoldenTest.request("unknown", "1000", "0", "0", "0"),
        BacktestEngineGoldenTest.request("ma-crossover", "-1", "0", "0", "0"), BacktestEngineGoldenTest.request("ma-crossover", "1000", "-1", "0", "0"),
        BacktestEngineGoldenTest.request("ma-crossover", "1000.001", "0", "0", "0"), BacktestEngineGoldenTest.request("ma-crossover", "1000000000001", "0", "0", "0"),
        BacktestEngineGoldenTest.request("ma-crossover", "1000", "0", "0.7", "0.3"), BacktestEngineGoldenTest.request("ma-crossover", "1000", "0", "1", "0"),
        BacktestEngineGoldenTest.request("ma-crossover", "1000", "0", "0.0014249999999999998", "0")))
      assertEquals("INVALID_INPUT", assertThrows(BacktestException.class, () -> service.run(request)).code());
    BacktestRequest invalid = new BacktestRequest(null, List.of("2330"), null, null, "ma-crossover", 2, 3, 2, Double.NaN, 55d, 3, 0.5d, 2, 20d, 20d, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, false);
    assertEquals("INVALID_INPUT", assertThrows(BacktestException.class, () -> service.run(invalid)).code());
    BacktestRequest nullSymbols = new BacktestRequest("2330", java.util.Arrays.asList("2330", null), "2024-01-01", "2024-01-10", "ma-crossover", 2, 3, null, null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, false);
    assertEquals("INVALID_INPUT", assertThrows(BacktestException.class, () -> service.run(nullSymbols)).code());
  }
  @Test void noHardcoded0050RetroactiveSplitAdjustmentRemains() {
    var service = new BacktestService(new CapturedClient(BacktestEngineGoldenTest.prices(100, 100, 100, 100, 100)), CLOCK);
    var request = new BacktestRequest("0050", null, "2024-01-01", "2024-01-05", "ma-crossover", 2, 3, null, null, null, null, null, null, null, null,
        BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, true);
    var response = service.run(request);
    assertEquals(10, response.results().get(1).trades().get(0).quantity());
    assertEquals(0, new BigDecimal("0.001").compareTo(response.metadata().resolvedConfig().appliedSellTaxRates().get("0050")));
    assertTrue(response.limitations().stream().anyMatch(l -> l.code().equals("TAX_CLASSIFICATION_ESTIMATE")));
  }
}
