package uk.kuronekoli.strategylab.robustness;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.market.DailyBar;

class RobustnessMatrixGoldenTest {
  private static BigDecimal bd(String value) { return new BigDecimal(value); }
  private static BacktestRequest base(String strategy) {
    return new BacktestRequest("2330", null, "2024-01-01", "2024-12-31", strategy, 2, 4,
        2, 30d, 55d, 3, 2d, 2, 20d, 20d, bd("0"), bd("10000"), bd("0"), bd("0"), false);
  }
  private static List<DailyBar> bars() {
    List<DailyBar> values = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      double close = 100 + i * 0.08 + Math.sin(i * 0.71) * 9;
      values.add(new DailyBar(LocalDate.of(2024, 1, 1).plusDays(i), BigDecimal.valueOf(close).setScale(4, java.math.RoundingMode.HALF_UP)));
    }
    return List.copyOf(values);
  }

  @Test void costVariantsProduceFiniteDistributionAndShowCostSensitivity() {
    var output = RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"), List.of(
        new RobustnessMatrix.Variant("no-cost", null, null, bd("0"), bd("0")),
        new RobustnessMatrix.Variant("with-cost", null, null, bd("0.02"), bd("0.01"))));
    assertEquals(List.of("no-cost", "with-cost"), output.cases().stream().map(RobustnessMatrix.CaseResult::id).toList());
    assertEquals(80, output.cases().get(0).sampleCount());
    assertNotEquals(output.cases().get(0).metrics().totalReturnPercent(), output.cases().get(1).metrics().totalReturnPercent());
    assertTrue(output.cases().stream().flatMap(c -> List.of(c.metrics().totalReturnPercent(), c.metrics().annualizedReturnPercent(),
        c.metrics().maxDrawdownPercent(), c.metrics().annualizedRealizedVolatilityPercent()).stream()).allMatch(Double::isFinite));
    assertEquals(Math.min(output.cases().get(0).metrics().totalReturnPercent(), output.cases().get(1).metrics().totalReturnPercent()),
        output.aggregate().totalReturnPercent().min());
    assertTrue(output.aggregate().totalReturnPercent().median() >= output.aggregate().totalReturnPercent().min());
    assertTrue(output.aggregate().totalReturnPercent().median() <= output.aggregate().totalReturnPercent().max());
  }

  @Test void sameInputsHaveSameOrderAndValues() {
    var variants = List.of(new RobustnessMatrix.Variant("slower", 3, 6, null, null),
        new RobustnessMatrix.Variant("costlier", null, null, bd("0.001"), bd("0.003")));
    var first = RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"), variants);
    var second = RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"), variants);
    assertEquals(first, second);
  }

  @Test void rejectsEmptyOversizedInvalidDuplicateAndStrategyIncompatibleMatrices() {
    assertThrows(RuntimeException.class, () -> RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"), List.of()));
    List<RobustnessMatrix.Variant> tooMany = new ArrayList<>();
    for (int i = 0; i <= RobustnessMatrix.MAX_CASES; i++)
      tooMany.add(new RobustnessMatrix.Variant("case-" + i, 2 + i, 80, null, null));
    assertThrows(RuntimeException.class, () -> RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"), tooMany));
    assertThrows(RuntimeException.class, () -> RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"),
        List.of(new RobustnessMatrix.Variant("bad", 9, 4, null, null))));
    assertThrows(RuntimeException.class, () -> RobustnessMatrix.run(base("rsi-reversion"), "2330", bars(), bd("0"),
        List.of(new RobustnessMatrix.Variant("wrong-params", 3, 6, null, null))));
    assertThrows(RuntimeException.class, () -> RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"),
        List.of(new RobustnessMatrix.Variant("bad-rate", null, null, bd("1"), null))));
    assertThrows(RuntimeException.class, () -> RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"),
        List.of(new RobustnessMatrix.Variant("same", 3, 6, null, null), new RobustnessMatrix.Variant("same", 4, 6, null, null))));
    assertThrows(RuntimeException.class, () -> RobustnessMatrix.run(base("ma-crossover"), "2330", bars(), bd("0"),
        List.of(new RobustnessMatrix.Variant("duplicate-a", 3, 6, null, null), new RobustnessMatrix.Variant("duplicate-b", 3, 6, null, null))));
  }
}
