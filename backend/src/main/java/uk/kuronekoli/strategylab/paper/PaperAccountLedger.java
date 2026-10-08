package uk.kuronekoli.strategylab.paper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** In-memory append-only ledger. A failed event is rejected without changing ledger or projection. */
public final class PaperAccountLedger {
  private final List<PaperAccountEvent> events = new ArrayList<>();
  private final Set<String> eventIds = new HashSet<>();

  public synchronized void append(PaperAccountEvent event) {
    if (eventIds.contains(event.eventId())) throw new IllegalArgumentException("duplicate eventId");
    rebuildWith(events, event); // Validate against current state before committing the event.
    events.add(event);
    eventIds.add(event.eventId());
  }

  public synchronized List<PaperAccountEvent> events() { return List.copyOf(events); }
  public synchronized PaperAccountProjection projection() { return rebuildWith(events, null); }

  public static PaperAccountProjection rebuild(List<PaperAccountEvent> events) {
    return rebuildWith(events, null);
  }

  private static PaperAccountProjection rebuildWith(List<PaperAccountEvent> events, PaperAccountEvent appended) {
    PaperAccountProjection result = new PaperAccountProjection();
    Set<String> seen = new HashSet<>();
    for (PaperAccountEvent event : events) {
      if (!seen.add(event.eventId())) throw new IllegalArgumentException("duplicate eventId");
      result.apply(event);
    }
    if (appended != null) {
      if (!seen.add(appended.eventId())) throw new IllegalArgumentException("duplicate eventId");
      result.apply(appended);
    }
    return result;
  }
}
