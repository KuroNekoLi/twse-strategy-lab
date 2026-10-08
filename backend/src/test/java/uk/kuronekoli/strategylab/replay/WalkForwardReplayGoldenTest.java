package uk.kuronekoli.strategylab.replay;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.kuronekoli.strategylab.market.DailyBar;

class WalkForwardReplayGoldenTest {
  private static BigDecimal d(String value) { return new BigDecimal(value); }
  private static List<DailyBar> fixture() {
    return List.of(new DailyBar(LocalDate.parse("2025-01-02"), d("10.00")),
        new DailyBar(LocalDate.parse("2025-01-03"), d("100.00")),
        new DailyBar(LocalDate.parse("2025-01-06"), d("20.00")));
  }

  @Test void revealsOneCloseAtATimeAndRequiresExplicitDecisionToAdvance() {
    var replay = new WalkForwardReplay(fixture(), d("100"), d("0"));
    assertEquals(new WalkForwardReplay.Observation(LocalDate.parse("2025-01-02"), d("10.00")), replay.observe());
    assertEquals(LocalDate.parse("2025-01-02"), replay.state().observation().date());
    assertEquals(0, replay.state().events().size());

    var afterWait = replay.decide(new WalkForwardReplay.Decision(WalkForwardReplay.Action.WAIT, d("0")));
    assertEquals(LocalDate.parse("2025-01-03"), afterWait.observation().date());
    assertEquals(d("100"), afterWait.cash());
    assertEquals(d("0"), afterWait.shares());
    assertEquals(1, afterWait.events().size());
    assertThrows(UnsupportedOperationException.class, () -> afterWait.events().clear());
  }

  @Test void exactCashShareAccountingAndReconstructionAreDeterministic() {
    var decisions = List.of(new WalkForwardReplay.Decision(WalkForwardReplay.Action.BUY, d("5")),
        new WalkForwardReplay.Decision(WalkForwardReplay.Action.SELL, d("2")),
        new WalkForwardReplay.Decision(WalkForwardReplay.Action.WAIT, d("0")));
    var expected = WalkForwardReplay.reconstruct(fixture(), d("100"), d("0.01"), decisions);
    var again = WalkForwardReplay.reconstruct(fixture(), d("100"), d("0.01"), decisions);
    assertEquals(expected, again);
    assertTrue(expected.complete());
    assertNull(expected.observation());
    assertEquals(d("0.50"), expected.events().get(0).fee());
    assertEquals(d("49.50"), expected.events().get(0).cashAfter());
    assertEquals(d("200.00"), expected.events().get(1).gross());
    assertEquals(d("2.00"), expected.events().get(1).fee());
    assertEquals(d("247.50"), expected.cash());
    assertEquals(d("3"), expected.shares());
  }

  @Test void rejectedDecisionDoesNotAdvanceOrCreateEvent() {
    var replay = new WalkForwardReplay(fixture(), d("50"), d("0.01"));
    assertThrows(IllegalArgumentException.class, () -> replay.decide(new WalkForwardReplay.Decision(WalkForwardReplay.Action.BUY, d("6"))));
    assertEquals(LocalDate.parse("2025-01-02"), replay.observe().date());
    assertTrue(replay.state().events().isEmpty());
    assertThrows(IllegalArgumentException.class, () -> replay.decide(new WalkForwardReplay.Decision(WalkForwardReplay.Action.SELL, d("1"))));
    assertEquals(LocalDate.parse("2025-01-02"), replay.observe().date());
  }

  @Test void rejectsInvalidTimelineAndDecisionShapes() {
    assertThrows(IllegalArgumentException.class, () -> new WalkForwardReplay(
        List.of(fixture().get(1), fixture().get(0)), d("10"), d("0")));
    assertThrows(IllegalArgumentException.class, () -> new WalkForwardReplay(fixture(), d("10"), d("1")));
    assertThrows(IllegalArgumentException.class, () -> new WalkForwardReplay.Decision(WalkForwardReplay.Action.WAIT, d("1")));
    assertThrows(IllegalArgumentException.class, () -> new WalkForwardReplay.Decision(WalkForwardReplay.Action.BUY, d("0")));
  }
}
