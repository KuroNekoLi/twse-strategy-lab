package uk.kuronekoli.strategylab.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderLifecycleGoldenTest {
  private static final Instant T0 = Instant.parse("2026-01-05T01:00:00Z");
  private static final LocalDate D0 = LocalDate.of(2026, 1, 5);

  private static OrderLifecycleEvent event(String id, OrderLifecycleEvent.Type type, int minute) {
    return new OrderLifecycleEvent(id, "order-1", type, T0.plusSeconds(minute * 60L), D0, null);
  }

  private static OrderLifecycleAggregate created() {
    return new OrderLifecycleAggregate(event("created", OrderLifecycleEvent.Type.CREATED, 0));
  }

  private static OrderLifecycleAggregate accepted() {
    OrderLifecycleAggregate order = created();
    order.append(event("submitted", OrderLifecycleEvent.Type.SUBMITTED, 1));
    order.append(event("accepted", OrderLifecycleEvent.Type.ACCEPTED, 2));
    return order;
  }

  @Test void supportsCreatedSubmittedAcceptedFilledPath() {
    OrderLifecycleAggregate order = created();
    assertEquals(OrderLifecycleAggregate.Status.CREATED, order.status());
    order.append(event("submitted", OrderLifecycleEvent.Type.SUBMITTED, 1));
    assertEquals(OrderLifecycleAggregate.Status.SUBMITTED, order.status());
    order.append(event("accepted", OrderLifecycleEvent.Type.ACCEPTED, 2));
    assertEquals(OrderLifecycleAggregate.Status.ACCEPTED, order.status());
    order.append(event("filled", OrderLifecycleEvent.Type.FILLED, 3));
    assertEquals(OrderLifecycleAggregate.Status.FILLED, order.status());
    assertEquals(List.of("created", "submitted", "accepted", "filled"),
        order.events().stream().map(OrderLifecycleEvent::eventId).toList());
    assertThrows(UnsupportedOperationException.class,
        () -> order.events().add(event("extra", OrderLifecycleEvent.Type.FILLED, 4)));
  }

  @Test void supportsRejectedFromSubmittedAndTerminalExitsFromAccepted() {
    OrderLifecycleAggregate rejected = created();
    rejected.append(event("submitted", OrderLifecycleEvent.Type.SUBMITTED, 1));
    rejected.append(event("rejected", OrderLifecycleEvent.Type.REJECTED, 2));
    assertEquals(OrderLifecycleAggregate.Status.REJECTED, rejected.status());

    for (var exit : List.of(OrderLifecycleEvent.Type.CANCELLED, OrderLifecycleEvent.Type.EXPIRED)) {
      OrderLifecycleAggregate order = accepted();
      order.append(event(exit.name(), exit, 3));
      assertEquals(OrderLifecycleAggregate.Status.valueOf(exit.name()), order.status());
    }
  }

  @Test void rejectsEveryIllegalTransitionWithoutMutatingState() {
    OrderLifecycleAggregate order = created();
    for (var type : List.of(OrderLifecycleEvent.Type.ACCEPTED, OrderLifecycleEvent.Type.FILLED,
        OrderLifecycleEvent.Type.REJECTED, OrderLifecycleEvent.Type.CANCELLED,
        OrderLifecycleEvent.Type.EXPIRED, OrderLifecycleEvent.Type.CREATED)) {
      assertThrows(IllegalArgumentException.class, () -> order.append(event("illegal-" + type, type, 1)));
      assertEquals(OrderLifecycleAggregate.Status.CREATED, order.status());
      assertEquals(1, order.events().size());
    }

    OrderLifecycleAggregate submitted = created();
    submitted.append(event("submitted", OrderLifecycleEvent.Type.SUBMITTED, 1));
    for (var type : List.of(OrderLifecycleEvent.Type.CREATED, OrderLifecycleEvent.Type.SUBMITTED,
        OrderLifecycleEvent.Type.FILLED, OrderLifecycleEvent.Type.CANCELLED,
        OrderLifecycleEvent.Type.EXPIRED)) {
      assertThrows(IllegalArgumentException.class, () -> submitted.append(event("illegal-" + type, type, 2)));
    }

    OrderLifecycleAggregate accepted = accepted();
    for (var type : List.of(OrderLifecycleEvent.Type.CREATED, OrderLifecycleEvent.Type.SUBMITTED,
        OrderLifecycleEvent.Type.ACCEPTED, OrderLifecycleEvent.Type.REJECTED)) {
      assertThrows(IllegalArgumentException.class, () -> accepted.append(event("illegal-" + type, type, 3)));
    }
  }

  @Test void everyTerminalStatusRejectsAllFurtherLifecycleEvents() {
    for (var terminal : List.of(OrderLifecycleEvent.Type.FILLED, OrderLifecycleEvent.Type.REJECTED,
        OrderLifecycleEvent.Type.CANCELLED, OrderLifecycleEvent.Type.EXPIRED)) {
      OrderLifecycleAggregate order = terminalOrder(terminal);
      int before = order.events().size();
      for (var attempted : OrderLifecycleEvent.Type.values()) {
        assertThrows(IllegalArgumentException.class,
            () -> order.append(event("after-" + terminal + "-" + attempted, attempted, 10)));
        assertEquals(OrderLifecycleAggregate.Status.valueOf(terminal.name()), order.status());
        assertEquals(before, order.events().size());
      }
    }
  }

  private static OrderLifecycleAggregate terminalOrder(OrderLifecycleEvent.Type terminal) {
    OrderLifecycleAggregate order = created();
    order.append(event("submitted", OrderLifecycleEvent.Type.SUBMITTED, 1));
    if (terminal == OrderLifecycleEvent.Type.REJECTED) {
      order.append(event("terminal", terminal, 2));
    } else {
      order.append(event("accepted", OrderLifecycleEvent.Type.ACCEPTED, 2));
      order.append(event("terminal", terminal, 3));
    }
    return order;
  }

  @Test void duplicateIdenticalEventIsNoOpAndConflictingPayloadIsRejected() {
    OrderLifecycleAggregate order = created();
    OrderLifecycleEvent submitted = event("submitted", OrderLifecycleEvent.Type.SUBMITTED, 1);
    order.append(submitted);
    order.append(submitted);
    assertEquals(2, order.events().size());
    assertEquals(OrderLifecycleAggregate.Status.SUBMITTED, order.status());

    var conflict = new OrderLifecycleEvent("submitted", "order-1", OrderLifecycleEvent.Type.SUBMITTED,
        T0.plusSeconds(90), D0, "different payload");
    assertThrows(IllegalArgumentException.class, () -> order.append(conflict));
    assertEquals(List.of("created", "submitted"), order.events().stream().map(OrderLifecycleEvent::eventId).toList());
  }

  @Test void enforcesMonotonicSuppliedTimesAndDatesAndKeepsStateOnFailure() {
    OrderLifecycleAggregate timeOrder = created();
    assertThrows(IllegalArgumentException.class, () -> timeOrder.append(new OrderLifecycleEvent(
        "submitted", "order-1", OrderLifecycleEvent.Type.SUBMITTED, T0.minusSeconds(1), D0, null)));
    assertEquals(1, timeOrder.events().size());

    OrderLifecycleAggregate dateOrder = created();
    dateOrder.append(new OrderLifecycleEvent("submitted", "order-1", OrderLifecycleEvent.Type.SUBMITTED,
        T0.plusSeconds(60), D0.plusDays(1), null));
    assertThrows(IllegalArgumentException.class, () -> dateOrder.append(new OrderLifecycleEvent(
        "accepted", "order-1", OrderLifecycleEvent.Type.ACCEPTED, T0.plusSeconds(120), D0, null)));
    assertEquals(OrderLifecycleAggregate.Status.SUBMITTED, dateOrder.status());
  }

  @Test void requiresOccurrenceTimeAndEffectiveDateForInitialAndLaterEvents() {
    assertThrows(NullPointerException.class, () -> new OrderLifecycleEvent("created-no-time", "order-1",
        OrderLifecycleEvent.Type.CREATED, null, D0, null));
    assertThrows(NullPointerException.class, () -> new OrderLifecycleEvent("created-no-date", "order-1",
        OrderLifecycleEvent.Type.CREATED, T0, null, null));
    OrderLifecycleAggregate order = created();
    assertThrows(NullPointerException.class, () -> new OrderLifecycleEvent("submitted-no-time", "order-1",
        OrderLifecycleEvent.Type.SUBMITTED, null, D0, null));
    assertThrows(NullPointerException.class, () -> new OrderLifecycleEvent("submitted-no-date", "order-1",
        OrderLifecycleEvent.Type.SUBMITTED, T0.plusSeconds(60), null, null));
    assertEquals(1, order.events().size());
    assertEquals(OrderLifecycleAggregate.Status.CREATED, order.status());
  }

  @Test void rejectsWrongOrderAndNonCreatedInitialEvent() {
    assertThrows(IllegalArgumentException.class,
        () -> new OrderLifecycleAggregate(event("not-created", OrderLifecycleEvent.Type.SUBMITTED, 0)));
    OrderLifecycleAggregate order = created();
    assertThrows(IllegalArgumentException.class, () -> order.append(new OrderLifecycleEvent(
        "foreign", "order-2", OrderLifecycleEvent.Type.SUBMITTED, T0.plusSeconds(60), D0, null)));
    assertEquals(1, order.events().size());
  }
}
