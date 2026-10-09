package uk.kuronekoli.strategylab.replay

import java.time.Instant
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action

interface ReplaySessionStore {
    data class SessionRecord(val id: String, val challengeId: String, val createdAt: Instant, val lastTouchedAt: Instant, val expiresAt: Instant)
    data class DecisionRecord(val sequenceNo: Int, val action: Action, val shares: Long?, val recordedAt: Instant)
    fun save(session: SessionRecord): SessionRecord
    fun find(id: String): java.util.Optional<SessionRecord>
    fun findExpired(now: Instant): List<SessionRecord>
    fun oldestFirst(): List<SessionRecord>
    fun decisions(sessionId: String): List<DecisionRecord>
    fun appendDecision(sessionId: String, decision: DecisionRecord)
    fun delete(sessionId: String)
}
