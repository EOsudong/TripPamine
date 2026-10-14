package com.example.trippaminebe.domain.ai.dto;

import java.time.LocalDateTime;
import java.util.List;


/**
 * AI Manager 전체 Context 응답.
 *
 * 지금까지 각각 분리되어 있던:
 *
 * - User
 * - Emotion
 * - Travel
 * - Quest
 * - Activity
 * - Memory
 *
 * 를 하나의 Context Snapshot으로 조립한다.
 *
 *
 * 이 DTO는 이후 TripPamine Manager SLM이
 * 사용자 상황을 이해하기 위한 기본 입력 경계가 된다.
 *
 *
 * 중요한 원칙:
 *
 * 1. DB Entity를 직접 노출하지 않는다.
 * 2. AI_USER_FACTS의 raw JSON을 직접 노출하지 않는다.
 * 3. 이미 검증된 Home / Memory DTO를 재사용한다.
 * 4. 향후 LLM/SLM이 DB 구조에 종속되지 않도록 한다.
 */
public record AiManagerContextResponse(

		/**
		 * 이 Context Snapshot이 생성된 기준 시각.
		 *
		 * Home Context와 동일한 기준 시각을 사용한다.
		 */
		LocalDateTime generatedAt,


		/**
		 * 현재 로그인 사용자 기본 Context.
		 */
		AiManagerHomeResponse.UserContext user,


		/**
		 * 가장 최근에 관찰된 감정 결과.
		 *
		 * 현재 자체 Emotion Model의 실제 결과이다.
		 */
		LatestEmotionResponse emotion,


		/**
		 * 현재 사용자의 Travel Context.
		 */
		AiManagerHomeResponse.TravelContext travel,


		/**
		 * 현재 사용자의 Quest Context.
		 */
		AiManagerHomeResponse.QuestContext quests,


		/**
		 * AI_MANAGER_EVENTS 기반 최근 Activity.
		 *
		 * 모델이 "최근 무슨 일이 있었는가"를
		 * 이해하기 위한 단기 Event Context 역할을 한다.
		 */
		List<AiManagerHomeResponse.ActivityEvent> activities,


		/**
		 * 사용자의 구조화 장기 Memory.
		 *
		 * 현재 포함:
		 *
		 * - Observation
		 * - Pattern Candidate
		 *
		 * Preference는 아직 포함하지 않는다.
		 */
		AiManagerMemoryResponse memory

) {
}