package uk.kuronekoli.strategylab.replay;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JpaReplaySessionStore implements ReplaySessionStore {
  private final ReplaySessionRepository sessions;
  private final ReplayDecisionRepository decisions;

  JpaReplaySessionStore(ReplaySessionRepository sessions, ReplayDecisionRepository decisions) {
    this.sessions = sessions;
    this.decisions = decisions;
  }

  @Override @Transactional
  public SessionRecord save(SessionRecord row) {
    var entity = sessions.findById(row.id()).orElseGet(ReplaySessionEntity::new);
    entity.id = row.id();
    entity.challengeId = row.challengeId();
    entity.createdAt = row.createdAt();
    entity.lastTouchedAt = row.lastTouchedAt();
    entity.expiresAt = row.expiresAt();
    sessions.save(entity);
    return row;
  }

  @Override @Transactional(readOnly = true)
  public Optional<SessionRecord> find(String id) {
    return sessions.findByIdForUpdate(id).map(JpaReplaySessionStore::record);
  }

  @Override @Transactional(readOnly = true)
  public List<SessionRecord> findExpired(Instant now) {
    return sessions.findByExpiresAtLessThanEqualOrderByLastTouchedAtAsc(now).stream().map(JpaReplaySessionStore::record).toList();
  }

  @Override @Transactional(readOnly = true)
  public List<SessionRecord> oldestFirst() {
    return sessions.findAllByOrderByLastTouchedAtAsc().stream().map(JpaReplaySessionStore::record).toList();
  }

  @Override @Transactional(readOnly = true)
  public List<DecisionRecord> decisions(String sessionId) {
    return decisions.findBySession_IdOrderBySequenceNoAsc(sessionId).stream()
        .map(row -> new DecisionRecord(row.sequenceNo, row.action, row.shares, row.recordedAt)).toList();
  }

  @Override @Transactional
  public void appendDecision(String sessionId, DecisionRecord row) {
    var session = sessions.findByIdForUpdate(sessionId).orElseThrow();
    decisions.save(new ReplayDecisionEntity(session, row.sequenceNo(), row.action(), row.shares(), row.recordedAt()));
  }

  @Override @Transactional
  public void delete(String sessionId) {
    decisions.deleteBySession_Id(sessionId);
    sessions.deleteById(sessionId);
  }

  private static SessionRecord record(ReplaySessionEntity row) {
    return new SessionRecord(row.id, row.challengeId, row.createdAt, row.lastTouchedAt, row.expiresAt);
  }
}
