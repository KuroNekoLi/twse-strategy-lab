package uk.kuronekoli.strategylab.replay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action;

@Entity
@Table(name = "replay_decision",
    uniqueConstraints = @UniqueConstraint(name = "uk_replay_decision_sequence", columnNames = {"session_id", "sequence_no"}),
    indexes = @Index(name = "idx_replay_decision_session", columnList = "session_id"))
class ReplayDecisionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_replay_decision_session"))
  ReplaySessionEntity session;

  @Column(name = "sequence_no", nullable = false)
  int sequenceNo;

  @Column(nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  Action action;

  @Column
  Long shares;

  @Column(name = "recorded_at", nullable = false)
  Instant recordedAt;

  protected ReplayDecisionEntity() {}

  ReplayDecisionEntity(ReplaySessionEntity session, int sequenceNo, Action action, Long shares, Instant recordedAt) {
    this.session = session;
    this.sequenceNo = sequenceNo;
    this.action = action;
    this.shares = shares;
    this.recordedAt = recordedAt;
  }
}
