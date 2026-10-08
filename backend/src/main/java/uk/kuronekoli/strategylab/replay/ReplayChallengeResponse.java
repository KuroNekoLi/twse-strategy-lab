package uk.kuronekoli.strategylab.replay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Allow-listed response DTOs: no private source bars or future observations are serialized. */
public record ReplayChallengeResponse(
    String sessionId,
    Challenge challenge,
    Observation observation,
    Portfolio state,
    List<DecisionEvent> events) {
  public ReplayChallengeResponse { events = List.copyOf(events); }

  public record Challenge(String id, String title, String label, String notice) {}
  public record Observation(LocalDate date, BigDecimal close) {}
  public record Portfolio(BigDecimal cash, BigDecimal shares, int decisionCount, boolean complete) {}
  public record DecisionEvent(LocalDate date, BigDecimal observedClose, String action,
      BigDecimal shares, BigDecimal cashAfter, BigDecimal sharesAfter) {}
}
