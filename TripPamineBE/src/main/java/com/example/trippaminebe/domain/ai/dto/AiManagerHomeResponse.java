package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * AI Manager Home 통합 응답.
 *
 * User / Emotion / Travel / Quest Context뿐 아니라
 * 이제 공식 Activity Event Timeline도 포함한다.
 */
public record AiManagerHomeResponse(

		LocalDateTime generatedAt,

		UserContext user,

		LatestEmotionResponse emotion,

		TravelContext travel,

		QuestContext quests,

		/**
		 * AI_MANAGER_EVENTS 기반 공식 Activity Feed.
		 */
		List<ActivityEvent> activities

) {

	/* =====================================================
	 * User
	 * ===================================================== */

	public record UserContext(

			Long userId,

			String userName,

			Long totalPoints,

			String grade,

			String profileImageUrl
	) {
	}


	/* =====================================================
	 * Travel
	 * ===================================================== */

	public record TravelContext(

			int totalPlanCount,

			FocusTravel focusPlan
	) {
	}


	public record FocusTravel(

			Long planId,

			String planName,

			BigDecimal totalBudget,

			String companionType,

			String locationCd,

			String blindYn,

			LocalDateTime startDate,

			LocalDateTime endDate,

			String state,

			Long daysUntilStart
	) {
	}


	/* =====================================================
	 * Quest
	 * ===================================================== */

	public record QuestContext(

			long activeCount,

			long completedCount,

			long failedCount,

			/**
			 * 현재 Context 용으로 유지한다.
			 *
			 * Activity Feed는 더 이상 이 값을 조합해서 만들지 않는다.
			 */
			List<RecentCompletedQuest> recentCompleted
	) {
	}


	public record RecentCompletedQuest(

			Long logId,

			Long questId,

			String questName,

			Long rewardPoint,

			LocalDateTime clearDate
	) {
	}


	/* =====================================================
	 * Activity
	 * ===================================================== */

	/**
	 * AI_MANAGER_EVENTS를 사용자 화면에 노출하기 위한 DTO.
	 *
	 * payloadJson 자체는 Frontend에 노출하지 않는다.
	 *
	 * 이유:
	 *
	 * 1. 내부 구현 Detail 숨김
	 * 2. 불필요한 개인정보 노출 방지
	 * 3. Frontend가 Event JSON Schema에 종속되는 것 방지
	 */
	public record ActivityEvent(

			Long eventId,

			/**
			 * emotion / quest / travel / finance / diary / system
			 */
			String domain,

			String eventType,

			String sourceType,

			String sourceId,

			String title,

			String description,

			LocalDateTime occurredAt
	) {
	}
}