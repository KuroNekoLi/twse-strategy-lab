package uk.kuronekoli.strategylab.replay;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action;

public interface ReplaySessionStore {
  record SessionRecord(String id, String challengeId, Instant createdAt, Instant lastTouchedAt, Instant expiresAt) {}
  record DecisionRecord(int sequenceNo, Action action, Long shares, Instant recordedAt) {}

  SessionRecord save(SessionRecord session);
  Optional<SessionRecord> find(String id);
  List<SessionRecord> findExpired(Instant now);
  List<SessionRecord> oldestFirst();
  List<DecisionRecord> decisions(String sessionId);
  void appendDecision(String sessionId, DecisionRecord decision);
  void delete(String sessionId);
}
