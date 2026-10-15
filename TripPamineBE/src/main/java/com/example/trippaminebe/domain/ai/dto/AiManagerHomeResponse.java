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

		// 기존의 최근 활동 기록
		List<ActivityEvent> activities,

		/**
		 * 신규: 이번 달 금융 정보.
		 *
		 * 금융 정보 활용에 동의하지 않았거나
		 * 아직 계산한 정보가 없다면 null이 된다.
		 *
		 * 사용자의 수입, 지출 등을
		 * 다른 화면에 다시 요청하지 않고
		 * AI Manager 홈에서 함께 받을 수 있다.
		 */
		AiFinanceMonthlySummaryResponse finance

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

	/**
	 * 현재 사용자의 Quest Context.
	 *
	 * 단순 개수뿐 아니라
	 * 현재 진행 중인 Quest의 상세 정보도 함께 제공한다.
	 *
	 * 이를 통해 이후 Priority Engine과 Manager SLM이
	 *
	 * "진행 중 Quest가 있다"
	 *
	 * 수준을 넘어서
	 *
	 * "어떤 Quest가 진행 중인가"
	 *
	 * 를 판단할 수 있다.
	 */
	public record QuestContext(

			long activeCount,

			long completedCount,

			long failedCount,

			/**
			 * 현재 실제로 진행 중인 Quest.
			 *
			 * QuestStatus.PROGRESS인 로그만 포함한다.
			 */
			List<ActiveQuest> activeQuests,

			/**
			 * 최근 완료된 Quest.
			 *
			 * 기존 Home UI용 Context를 그대로 유지한다.
			 */
			List<RecentCompletedQuest> recentCompleted

	) {
	}


	/**
	 * 현재 진행 중인 Quest 상세 Context.
	 *
	 * 현재 Quest Domain이 실제로 제공하는 정보만 담는다.
	 *
	 * 아직 제공하지 않는:
	 *
	 * - deadline
	 * - expiresAt
	 * - travelPlanId
	 *
	 * 등은 임의로 생성하지 않는다.
	 */
	public record ActiveQuest(

			Long logId,

			Long questId,

			String questName,

			Long rewardPoint,

			Long rewardExp,

			LocalDateTime startedAt,

			/**
			 * CHAIN Quest라면 현재 완료 Step 수.
			 *
			 * 일반 Quest 또는 계산되지 않은 경우 null 가능.
			 */
			Integer clearedStepCount,

			/**
			 * CHAIN Quest의 전체 Step 수.
			 *
			 * 일반 Quest 또는 계산되지 않은 경우 null 가능.
			 */
			Integer totalStepCount

	) {
	}


	/**
	 * 최근 완료된 Quest.
	 */
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