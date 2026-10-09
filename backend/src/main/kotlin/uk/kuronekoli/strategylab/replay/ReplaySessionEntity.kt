package uk.kuronekoli.strategylab.replay

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "replay_session", indexes = [Index(name = "idx_replay_session_expires", columnList = "expires_at")])
class ReplaySessionEntity {
    @Id @Column(length = 36, nullable = false) lateinit var id: String
    @Column(name = "challenge_id", length = 64, nullable = false) lateinit var challengeId: String
    @Column(name = "created_at", nullable = false) lateinit var createdAt: Instant
    @Column(name = "last_touched_at", nullable = false) lateinit var lastTouchedAt: Instant
    @Column(name = "expires_at", nullable = false) lateinit var expiresAt: Instant
    @Version var version: Long = 0
    constructor()
    constructor(id: String, challengeId: String, createdAt: Instant, lastTouchedAt: Instant, expiresAt: Instant) { this.id=id; this.challengeId=challengeId; this.createdAt=createdAt; this.lastTouchedAt=lastTouchedAt; this.expiresAt=expiresAt }
}
