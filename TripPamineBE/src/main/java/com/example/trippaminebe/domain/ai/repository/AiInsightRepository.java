package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiInsight;
import com.example.trippaminebe.domain.ai.entity.AiInsightStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiInsightRepository
		extends JpaRepository<AiInsight, Long> {

	List<AiInsight>
	findTop20ByUser_IdAndStatusOrderByCreatedAtDesc(
			Long userId,
			AiInsightStatus status
	);

	List<AiInsight>
	findTop20ByTravelPlan_PlanIdAndStatusOrderByCreatedAtDesc(
			Long planId,
			AiInsightStatus status
	);
}