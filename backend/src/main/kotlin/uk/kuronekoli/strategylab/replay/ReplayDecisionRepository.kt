package uk.kuronekoli.strategylab.replay

import org.springframework.data.jpa.repository.JpaRepository

interface ReplayDecisionRepository : JpaRepository<ReplayDecisionEntity, Long> {
    fun findBySession_IdOrderBySequenceNoAsc(sessionId: String): List<ReplayDecisionEntity>
    fun deleteBySession_Id(sessionId: String)
}
