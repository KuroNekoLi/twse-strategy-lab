package uk.kuronekoli.strategylab.replay;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ReplayDecisionRepository extends JpaRepository<ReplayDecisionEntity, Long> {
  List<ReplayDecisionEntity> findBySession_IdOrderBySequenceNoAsc(String sessionId);
  void deleteBySession_Id(String sessionId);
}
