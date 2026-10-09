package uk.kuronekoli.strategylab.replay

import java.time.Instant
import java.util.Optional
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.kuronekoli.strategylab.replay.ReplaySessionStore.DecisionRecord
import uk.kuronekoli.strategylab.replay.ReplaySessionStore.SessionRecord

@Repository
class JpaReplaySessionStore(private val sessions: ReplaySessionRepository, private val decisions: ReplayDecisionRepository) : ReplaySessionStore {
    @Transactional override fun save(row: SessionRecord): SessionRecord {
        val entity = sessions.findById(row.id).orElseGet { ReplaySessionEntity() }
        entity.id=row.id; entity.challengeId=row.challengeId; entity.createdAt=row.createdAt; entity.lastTouchedAt=row.lastTouchedAt; entity.expiresAt=row.expiresAt
        sessions.save(entity); return row
    }
    @Transactional(readOnly = true) override fun find(id: String): Optional<SessionRecord> = sessions.findByIdForUpdate(id).map(::record)
    @Transactional(readOnly = true) override fun findExpired(now: Instant): List<SessionRecord> = sessions.findByExpiresAtLessThanEqualOrderByLastTouchedAtAsc(now).map(::record)
    @Transactional(readOnly = true) override fun oldestFirst(): List<SessionRecord> = sessions.findAllByOrderByLastTouchedAtAsc().map(::record)
    @Transactional(readOnly = true) override fun decisions(sessionId: String): List<DecisionRecord> = decisions.findBySession_IdOrderBySequenceNoAsc(sessionId).map { DecisionRecord(it.sequenceNo,it.action,it.shares,it.recordedAt) }
    @Transactional override fun appendDecision(sessionId: String, row: DecisionRecord) { val session=sessions.findByIdForUpdate(sessionId).orElseThrow(); decisions.save(ReplayDecisionEntity(session,row.sequenceNo,row.action,row.shares,row.recordedAt)) }
    @Transactional override fun delete(sessionId: String) { decisions.deleteBySession_Id(sessionId); sessions.deleteById(sessionId) }
    private fun record(row: ReplaySessionEntity) = SessionRecord(row.id,row.challengeId,row.createdAt,row.lastTouchedAt,row.expiresAt)
}
