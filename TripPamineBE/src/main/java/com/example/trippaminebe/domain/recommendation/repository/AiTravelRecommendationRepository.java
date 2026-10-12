package com.example.trippaminebe.domain.recommendation.repository;

import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiTravelRecommendationRepository
    extends JpaRepository<AiTravelRecommendation, Long> {

  Optional<AiTravelRecommendation> findByTravelPlan_PlanId(Long planId);

  /**
   * AI 재계획 복구 시 동시 수정 방지를 위한 DB 잠금 조회
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
    SELECT r
    FROM AiTravelRecommendation r
    WHERE r.travelPlan.planId = :planId
    """)
  Optional<AiTravelRecommendation> findByPlanIdForUpdate(
          @Param("planId") Long planId
  );

  boolean existsByTravelPlan_PlanId(Long planId);
}
