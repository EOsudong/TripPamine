package com.example.trippaminebe.domain.recommendation.repository;

import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiTravelRecommendationRepository
    extends JpaRepository<AiTravelRecommendation, Long> {

  Optional<AiTravelRecommendation> findByTravelPlan_PlanId(Long planId);

  boolean existsByTravelPlan_PlanId(Long planId);
}
