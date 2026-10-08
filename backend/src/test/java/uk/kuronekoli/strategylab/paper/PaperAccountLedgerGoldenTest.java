package uk.kuronekoli.strategylab.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperAccountLedgerGoldenTest {
  private static final LocalDate DAY = LocalDate.of(2026, 1, 5);
  private static BigDecimal bd(String value) { return new BigDecimal(value); }
  private static void money(String expected, BigDecimal actual) { assertEquals(0, bd(expected).compareTo(actual)); }

  @Test void cashAndPositionReconcileWithCentRoundedFeeAndTax() {
    PaperAccountLedger ledger = new PaperAccountLedger();
    ledger.append(PaperAccountEvent.initialDeposit("deposit-1", DAY, bd("1000.00")));
    ledger.append(PaperAccountEvent.buy("buy-1", DAY, "2330", 5, bd("100.00"), bd("0.01")));
    ledger.append(PaperAccountEvent.sell("sell-1", DAY.plusDays(1), "2330", 2,
        bd("120.00"), bd("0.01"), bd("0.003")));

    PaperAccountProjection state = ledger.projection();
    // Buy 500 + 5 fee; sale nets 240 - 2.40 fee - 0.72 tax.
    money("731.88", state.cash());
    assertEquals(3, state.position("2330").quantity());
    money("303.00", state.position("2330").costBasis());
    assertEquals(3, ledger.events().size());
  }

  @Test void rejectsInsufficientCashAndSharesWithoutChangingLedger() {
    PaperAccountLedger ledger = new PaperAccountLedger();
    ledger.append(PaperAccountEvent.initialDeposit("d", DAY, bd("99.99")));
    assertThrows(IllegalArgumentException.class,
        () -> ledger.append(PaperAccountEvent.buy("too-much", DAY, "2330", 1, bd("100"), bd("0"))));
    assertEquals(1, ledger.events().size()); money("99.99", ledger.projection().cash());

    assertEquals(1, ledger.events().size());
  }

  @Test void rejectsInsufficientSharesAndAllowsLedgerToRemainRebuildable() {
    PaperAccountLedger ledger = new PaperAccountLedger();
    ledger.append(PaperAccountEvent.initialDeposit("d", DAY, bd("200")));
    ledger.append(PaperAccountEvent.buy("b", DAY, "2330", 1, bd("100"), bd("0")));
    assertThrows(IllegalArgumentException.class,
        () -> ledger.append(PaperAccountEvent.sell("s-too-many", DAY, "2330", 2, bd("110"), bd("0"), bd("0"))));
    assertEquals(2, ledger.events().size());
    assertEquals(1, ledger.projection().position("2330").quantity());
  }

  @Test void duplicateEventIdsAreRejectedAndDoNotMutateState() {
    PaperAccountLedger ledger = new PaperAccountLedger();
    var deposit = PaperAccountEvent.initialDeposit("same", DAY, bd("200"));
    ledger.append(deposit);
    assertThrows(IllegalArgumentException.class,
        () -> ledger.append(PaperAccountEvent.initialDeposit("same", DAY, bd("100"))));
    assertEquals(List.of(deposit), ledger.events()); money("200.00", ledger.projection().cash());
  }

  @Test void rebuildingSameEventSequenceProducesIdenticalProjection() {
    List<PaperAccountEvent> events = List.of(
        PaperAccountEvent.initialDeposit("d", DAY, bd("1000")),
        PaperAccountEvent.buy("b", DAY, "2330", 4, bd("100"), bd("0.01")),
        PaperAccountEvent.sell("s", DAY.plusDays(1), "2330", 1, bd("110"), bd("0.01"), bd("0.003")));
    PaperAccountProjection first = PaperAccountLedger.rebuild(events);
    PaperAccountProjection second = PaperAccountLedger.rebuild(List.copyOf(events));
    assertEquals(first.cash(), second.cash()); money("704.57", first.cash());
    assertEquals(first.positions(), second.positions());
    assertEquals(3, first.position("2330").quantity()); money("303.00", first.position("2330").costBasis());
  }

  @Test void rejectsPriceOrDepositThatWouldRoundToZeroAndPreventFreeShares() {
    assertThrows(IllegalArgumentException.class,
        () -> PaperAccountEvent.initialDeposit("tiny-deposit", DAY, bd("0.004")));
    assertThrows(IllegalArgumentException.class,
        () -> PaperAccountEvent.buy("tiny-buy", DAY, "2330", 1, bd("0.004"), bd("0")));
    PaperAccountLedger ledger = new PaperAccountLedger();
    ledger.append(PaperAccountEvent.initialDeposit("d", DAY, bd("1.00")));
    assertThrows(IllegalArgumentException.class,
        () -> ledger.append(PaperAccountEvent.buy("tiny-buy", DAY, "2330", 1, bd("0.004"), bd("0"))));
    assertTrue(ledger.projection().positions().isEmpty());
    assertEquals(1, ledger.events().size());
  }
}
