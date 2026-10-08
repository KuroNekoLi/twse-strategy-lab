package uk.kuronekoli.strategylab.replay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "replay_session", indexes = @Index(name = "idx_replay_session_expires", columnList = "expires_at"))
class ReplaySessionEntity {
  @Id
  @Column(length = 36, nullable = false)
  String id;

  @Column(name = "challenge_id", length = 64, nullable = false)
  String challengeId;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "last_touched_at", nullable = false)
  Instant lastTouchedAt;

  @Column(name = "expires_at", nullable = false)
  Instant expiresAt;

  @Version
  long version;

  protected ReplaySessionEntity() {}

  ReplaySessionEntity(String id, String challengeId, Instant createdAt, Instant lastTouchedAt, Instant expiresAt) {
    this.id = id;
    this.challengeId = challengeId;
    this.createdAt = createdAt;
    this.lastTouchedAt = lastTouchedAt;
    this.expiresAt = expiresAt;
  }
}
