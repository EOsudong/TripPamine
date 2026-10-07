package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiEmotionObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


/**
 * AI 감정 관측(AI_EMOTION_OBSERVATIONS) Repository.
 *
 * 이 Repository의 역할은 크게 두 가지다.
 *
 * 1. 최신 감정 상태 조회
 * 2. AI_MANAGER_EVENTS와 연결된 감정 Observation 일괄 조회
 *
 *
 * =========================================================
 * 왜 ObservedAt 기준으로 최신 감정을 조회하는가?
 * =========================================================
 *
 * AI_EMOTION_OBSERVATIONS에는 보통:
 *
 * - OBSERVED_AT
 * - CREATED_AT
 *
 * 두 시각이 존재한다.
 *
 * CREATED_AT:
 * DB Row가 저장된 시점
 *
 * OBSERVED_AT:
 * 실제 감정이 관찰된 시점
 *
 * 사용자의 "가장 최근 감정"을 판단할 때는
 * 실제 관측 시각인 OBSERVED_AT이 더 적절하다.
 *
 * 따라서 EmotionQueryService에서는
 * OrderByObservedAtDesc를 사용한다.
 */
public interface AiEmotionObservationRepository
		extends JpaRepository<AiEmotionObservation, Long> {


	/* =====================================================
	 * 최신 감정 조회
	 * ===================================================== */

	/**
	 * 사용자의 전체 감정 Observation 중
	 * 가장 최근에 관찰된 1건을 조회한다.
	 *
	 * Spring Data JPA가 메서드 이름을 분석해서
	 * 자동으로 Query를 생성한다.
	 *
	 * 개념적인 SQL:
	 *
	 * SELECT *
	 * FROM AI_EMOTION_OBSERVATIONS
	 * WHERE USER_ID = ?
	 * ORDER BY OBSERVED_AT DESC
	 * FETCH FIRST 1 ROW ONLY;
	 */
	Optional<AiEmotionObservation>
	findTopByUser_IdOrderByObservedAtDesc(
			Long userId
	);


	/**
	 * 특정 사용자의 특정 여행에 연결된 감정 중
	 * 가장 최근 Observation 1건을 조회한다.
	 *
	 * 사용자 ID까지 함께 조건에 넣는 이유:
	 *
	 * planId만 조회하면 잘못된 소유권 처리 시
	 * 다른 사용자의 여행 감정 데이터를 읽을 가능성이 생긴다.
	 *
	 * 따라서:
	 *
	 * USER_ID
	 * +
	 * PLAN_ID
	 *
	 * 를 함께 조회 조건으로 사용한다.
	 */
	Optional<AiEmotionObservation>
	findTopByUser_IdAndTravelPlan_PlanIdOrderByObservedAtDesc(
			Long userId,
			Long planId
	);


	/* =====================================================
	 * Legacy / 범용 조회
	 * ===================================================== */

	/**
	 * 기존 코드와의 호환을 위해 유지한다.
	 *
	 * CREATED_AT 기준 최신 Observation 조회.
	 *
	 * 현재 EmotionQueryService는 OBSERVED_AT을 사용하지만
	 * 다른 Service가 이 메서드를 사용할 가능성이 있으므로
	 * 제거하지 않는다.
	 */
	Optional<AiEmotionObservation>
	findTopByUser_IdOrderByCreatedAtDesc(
			Long userId
	);


	/**
	 * 특정 여행의 Observation 중
	 * CREATED_AT 기준 최근 1건.
	 *
	 * 기존 구현과의 호환성을 위해 유지한다.
	 */
	Optional<AiEmotionObservation>
	findTopByTravelPlan_PlanIdOrderByCreatedAtDesc(
			Long planId
	);


	/* =====================================================
	 * AI Manager Activity Feed
	 * ===================================================== */

	/**
	 * 여러 AI_MANAGER_EVENTS에 연결된
	 * 감정 Observation을 한 번에 조회한다.
	 *
	 * AI_EMOTION_OBSERVATIONS.EVENT_ID
	 *
	 * 가
	 *
	 * AI_MANAGER_EVENTS.EVENT_ID
	 *
	 * 를 참조하고 있기 때문에 사용할 수 있다.
	 *
	 *
	 * =====================================================
	 * 왜 이 메서드가 필요한가?
	 * =====================================================
	 *
	 * 나쁜 방식:
	 *
	 * Event 1
	 * → Emotion SELECT
	 *
	 * Event 2
	 * → Emotion SELECT
	 *
	 * Event 3
	 * → Emotion SELECT
	 *
	 * ...
	 *
	 * Event가 20개라면 최대 20개의 추가 SELECT가 발생한다.
	 * 이것을 흔히 N+1 문제라고 한다.
	 *
	 * 개선 방식:
	 *
	 * Event 20개 조회
	 *       ↓
	 * EVENT_ID = [1, 2, 3 ...]
	 *       ↓
	 * WHERE EVENT_ID IN (...)
	 *       ↓
	 * Emotion Observation 한 번에 조회
	 *
	 * 따라서 DB 접근 횟수를 크게 줄일 수 있다.
	 */
	List<AiEmotionObservation>
	findByEvent_EventIdIn(
			Collection<Long> eventIds
	);
}