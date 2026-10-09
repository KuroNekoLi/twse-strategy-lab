package uk.kuronekoli.strategylab.replay

import jakarta.persistence.*
import java.time.Instant
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action

@Entity
@Table(name = "replay_decision", uniqueConstraints = [UniqueConstraint(name = "uk_replay_decision_sequence", columnNames = ["session_id", "sequence_no"])], indexes = [Index(name = "idx_replay_decision_session", columnList = "session_id")])
class ReplayDecisionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false, foreignKey = ForeignKey(name = "fk_replay_decision_session")) lateinit var session: ReplaySessionEntity
    @Column(name = "sequence_no", nullable = false) var sequenceNo: Int = 0
    @Column(nullable = false, length = 8) @Enumerated(EnumType.STRING) lateinit var action: Action
    @Column var shares: Long? = null
    @Column(name = "recorded_at", nullable = false) lateinit var recordedAt: Instant
    constructor()
    constructor(session: ReplaySessionEntity, sequenceNo: Int, action: Action, shares: Long?, recordedAt: Instant) { this.session=session; this.sequenceNo=sequenceNo; this.action=action; this.shares=shares; this.recordedAt=recordedAt }
}
