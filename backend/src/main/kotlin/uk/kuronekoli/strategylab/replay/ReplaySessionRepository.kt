package uk.kuronekoli.strategylab.replay

import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.Optional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ReplaySessionRepository : JpaRepository<ReplaySessionEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReplaySessionEntity s where s.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<ReplaySessionEntity>
    fun findByExpiresAtLessThanEqualOrderByLastTouchedAtAsc(expiry: Instant): List<ReplaySessionEntity>
    fun findAllByOrderByLastTouchedAtAsc(): List<ReplaySessionEntity>
}
