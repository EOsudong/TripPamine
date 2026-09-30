package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiEmotionObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiEmotionObservationRepository
		extends JpaRepository<AiEmotionObservation, Long> {

	/**
	 * 사용자의 전체 감정 관측 중 가장 최근 1건.
	 */
	Optional<AiEmotionObservation>
	findTopByUser_IdOrderByObservedAtDesc(
			Long userId
	);

	/**
	 * 특정 여행의 가장 최근 감정.
	 *
	 * 기존 코드와의 호환 때문에 유지하지만,
	 * 사용자 API에서는 아래 userId + planId 메서드를 사용한다.
	 */
	Optional<AiEmotionObservation>
	findTopByTravelPlan_PlanIdOrderByObservedAtDesc(
			Long planId
	);

	/**
	 * 로그인 사용자 + 여행 계획을 함께 제한하여
	 * 다른 사용자의 감정 정보가 노출되는 것을 방지한다.
	 */
	Optional<AiEmotionObservation>
	findTopByUser_IdAndTravelPlan_PlanIdOrderByObservedAtDesc(
			Long userId,
			Long planId
	);
}