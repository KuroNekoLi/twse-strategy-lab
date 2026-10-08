package uk.kuronekoli.strategylab.paper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure in-memory order state machine; no partial fills, market feed, persistence, or broker calls. */
public final class OrderLifecycleAggregate {
  public enum Status { CREATED, SUBMITTED, ACCEPTED, FILLED, REJECTED, CANCELLED, EXPIRED }

  private final String orderId;
  private final List<OrderLifecycleEvent> events = new ArrayList<>();
  private final Map<String, OrderLifecycleEvent> eventsById = new HashMap<>();
  private Status status;
  private Instant lastOccurredAt;
  private LocalDate lastEffectiveDate;

  public OrderLifecycleAggregate(OrderLifecycleEvent created) {
    Objects.requireNonNull(created, "created event is required");
    if (created.type() != OrderLifecycleEvent.Type.CREATED)
      throw new IllegalArgumentException("first event must be CREATED");
    orderId = created.orderId();
    append(created);
  }

  /** Appends a transition. An exact retry by event ID is a no-op; conflicting reuse is rejected. */
  public synchronized void append(OrderLifecycleEvent event) {
    Objects.requireNonNull(event, "event is required");
    OrderLifecycleEvent prior = eventsById.get(event.eventId());
    if (prior != null) {
      if (prior.equals(event)) return;
      throw new IllegalArgumentException("eventId was already used with a different payload");
    }
    if (!orderId.equals(event.orderId())) throw new IllegalArgumentException("event orderId does not match aggregate");
    Status next = nextStatus(status, event.type());
    if (next == null) throw new IllegalArgumentException("illegal order transition: " + status + " -> " + event.type());
    if (lastOccurredAt != null && event.occurredAt() != null && event.occurredAt().isBefore(lastOccurredAt))
      throw new IllegalArgumentException("occurredAt must be monotonic");
    if (lastEffectiveDate != null && event.effectiveDate() != null && event.effectiveDate().isBefore(lastEffectiveDate))
      throw new IllegalArgumentException("effectiveDate must be monotonic");

    events.add(event);
    eventsById.put(event.eventId(), event);
    status = next;
    if (event.occurredAt() != null) lastOccurredAt = event.occurredAt();
    if (event.effectiveDate() != null) lastEffectiveDate = event.effectiveDate();
  }

  public synchronized Status status() { return status; }
  public String orderId() { return orderId; }
  public synchronized List<OrderLifecycleEvent> events() { return List.copyOf(events); }

  private static Status nextStatus(Status current, OrderLifecycleEvent.Type type) {
    if (current == null) return type == OrderLifecycleEvent.Type.CREATED ? Status.CREATED : null;
    return switch (current) {
      case CREATED -> type == OrderLifecycleEvent.Type.SUBMITTED ? Status.SUBMITTED : null;
      case SUBMITTED -> switch (type) {
        case ACCEPTED -> Status.ACCEPTED;
        case REJECTED -> Status.REJECTED;
        default -> null;
      };
      case ACCEPTED -> switch (type) {
        case FILLED -> Status.FILLED;
        case CANCELLED -> Status.CANCELLED;
        case EXPIRED -> Status.EXPIRED;
        default -> null;
      };
      case FILLED, REJECTED, CANCELLED, EXPIRED -> null;
    };
  }
}
