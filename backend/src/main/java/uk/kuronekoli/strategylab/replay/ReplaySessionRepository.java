package uk.kuronekoli.strategylab.replay;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

interface ReplaySessionRepository extends JpaRepository<ReplaySessionEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from ReplaySessionEntity s where s.id = :id")
  Optional<ReplaySessionEntity> findByIdForUpdate(@Param("id") String id);

  List<ReplaySessionEntity> findByExpiresAtLessThanEqualOrderByLastTouchedAtAsc(Instant expiry);
  List<ReplaySessionEntity> findAllByOrderByLastTouchedAtAsc();
}
