package uk.kuronekoli.strategylab.robustness;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.market.TwseMarketDataClient;

class RobustnessServiceTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-02-15T04:00:00Z"), ZoneId.of("Asia/Taipei"));

  private static final class CapturedClient extends TwseMarketDataClient {
    private final List<DailyBar> bars;
    int loads;
    YearMonth first, last;
    CapturedClient(List<DailyBar> bars) { super(JsonMapper.builder().build(), "https://example.invalid/STOCK_DAY"); this.bars = bars; }
    @Override public List<DailyBar> load(String symbol, YearMonth first, YearMonth last) {
      loads++; this.first = first; this.last = last; return bars;
    }
  }

  private static BacktestRequest base(String to) {
    return new BacktestRequest("2330", null, "2024-01-01", to, "ma-crossover", 2, 4,
        null, null, null, null, null, null, null, null, BigDecimal.ZERO, new BigDecimal("10000"),
        BigDecimal.ZERO, BigDecimal.ZERO, false);
  }
  private static List<DailyBar> bars() {
    List<DailyBar> rows = new ArrayList<>();
    for (int i = 0; i < 25; i++) rows.add(new DailyBar(LocalDate.of(2024, 1, 1).plusDays(i), BigDecimal.valueOf(100 + i)));
    return List.copyOf(rows);
  }
  private static RobustnessRequest request(String to) {
    return new RobustnessRequest("base-v1", base(to), List.of(
        new RobustnessMatrix.Variant("base-cost", null, null, BigDecimal.ZERO, null),
        new RobustnessMatrix.Variant("higher-cost", null, null, new BigDecimal("0.001"), new BigDecimal("0.003"))));
  }

  @Test void loadsBarsOnceAndReturnsCaseIdsSamplesAndResearchCaveats() {
    var client = new CapturedClient(bars());
    var response = new RobustnessService(client, CLOCK).run(request("2024-12-31"));

    assertEquals(1, client.loads);
    assertEquals(YearMonth.of(2024, 1), client.first);
    assertEquals(YearMonth.of(2024, 2), client.last); // Request is clamped to the injected current date.
    assertEquals("base-v1", response.baseId());
    assertEquals("LIMITED_RESEARCH", response.status());
    assertEquals(25, response.sampleCount());
    assertEquals("2024-01-25", response.observedTo());
    assertEquals(List.of("base-cost", "higher-cost"), response.cases().stream().map(RobustnessMatrix.CaseResult::id).toList());
    assertTrue(response.cases().stream().allMatch(c -> c.sampleCount() == 25));
    assertTrue(response.limitations().stream().anyMatch(s -> s.contains("LICENSING_UNVERIFIED")));
    assertTrue(response.limitations().stream().anyMatch(s -> s.contains("不代表策略有效")));
  }

  @Test void rejectsInvalidBaseAndOverLimitCasesBeforeMarketLoad() {
    var client = new CapturedClient(bars());
    var service = new RobustnessService(client, CLOCK);
    var badDate = new RobustnessRequest("base-v1", base("not-a-date"), request("2024-01-10").variants());
    assertEquals("INVALID_INPUT", assertThrows(BacktestException.class, () -> service.run(badDate)).code());
    var invalidCost = new RobustnessRequest("base-v1", base("2024-01-10"), List.of(
        new RobustnessMatrix.Variant("bad-cost", null, null, new BigDecimal("1"), BigDecimal.ZERO)));
    assertEquals("INVALID_INPUT", assertThrows(BacktestException.class, () -> service.run(invalidCost)).code());
    List<RobustnessMatrix.Variant> oversized = new ArrayList<>();
    for (int i = 0; i <= RobustnessMatrix.MAX_CASES; i++)
      oversized.add(new RobustnessMatrix.Variant("case-" + i, 2 + i, 40, null, null));
    assertEquals("INVALID_INPUT", assertThrows(BacktestException.class,
        () -> service.run(new RobustnessRequest("base-v1", base("2024-01-10"), oversized))).code());
    assertEquals(0, client.loads);
  }

  @Test void reportsNoBarsUsingExistingApiException() {
    var client = new CapturedClient(List.of());
    var error = assertThrows(BacktestException.class,
        () -> new RobustnessService(client, CLOCK).run(request("2024-01-10")));
    assertEquals("DATA_INTEGRITY_FAILED", error.code());
    assertTrue(error.getMessage().contains("行情資料為空"));
    assertEquals(1, client.loads);
  }
}
