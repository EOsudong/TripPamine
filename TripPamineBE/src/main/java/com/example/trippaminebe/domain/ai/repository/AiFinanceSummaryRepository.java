package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiFinancePeriodType;
import com.example.trippaminebe.domain.ai.entity.AiFinanceSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiFinanceSummaryRepository
		extends JpaRepository<AiFinanceSummary, Long> {

	Optional<AiFinanceSummary>
	findTopByUser_IdAndPeriodTypeOrderByAsOfAtDesc(
			Long userId,
			AiFinancePeriodType periodType
	);

	Optional<AiFinanceSummary>
	findTopByTravelPlan_PlanIdOrderByAsOfAtDesc(
			Long planId
	);
}