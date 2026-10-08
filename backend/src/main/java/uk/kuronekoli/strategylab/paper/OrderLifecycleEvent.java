package uk.kuronekoli.strategylab.paper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable synthetic lifecycle fact. It does not represent a broker or exchange event. */
public record OrderLifecycleEvent(
    String eventId,
    String orderId,
    Type type,
    Instant occurredAt,
    LocalDate effectiveDate,
    String reason) {

  public enum Type { CREATED, SUBMITTED, ACCEPTED, FILLED, REJECTED, CANCELLED, EXPIRED }

  public OrderLifecycleEvent {
    if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
    if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId is required");
    Objects.requireNonNull(type, "type is required");
    Objects.requireNonNull(occurredAt, "occurredAt is required");
    Objects.requireNonNull(effectiveDate, "effectiveDate is required");
    if (reason != null && reason.isBlank()) reason = null;
  }
}
