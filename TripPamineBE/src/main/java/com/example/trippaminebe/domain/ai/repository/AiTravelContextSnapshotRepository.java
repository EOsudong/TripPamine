package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiTravelContextSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiTravelContextSnapshotRepository
		extends JpaRepository<AiTravelContextSnapshot, Long> {

	Optional<AiTravelContextSnapshot>
	findTopByTravelPlan_PlanIdOrderByAsOfAtDesc(
			Long planId
	);

	Optional<AiTravelContextSnapshot>
	findTopByUser_IdOrderByAsOfAtDesc(
			Long userId
	);

	Optional<AiTravelContextSnapshot>
	findTopByTravelPlan_PlanIdAndContextHashOrderByAsOfAtDesc(
			Long planId,
			String contextHash
	);
}