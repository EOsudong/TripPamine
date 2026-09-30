package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI Manager Home 통합 응답.
 *
 * <p>
 * 프론트가 사용자 / 감정 / 여행 / 퀘스트 API를 각각 호출하지 않고,
 * AI Manager가 현재 사용자 Context를 한 번에 내려주기 위한 DTO입니다.
 * </p>
 *
 * <p>
 * 현재 STEP B에서는 "Context 집계"까지만 담당합니다.
 * 어떤 항목이 더 중요한지 판단하는 Priority Engine은 다음 단계에서 추가합니다.
 * </p>
 */
public record AiManagerHomeResponse(

		LocalDateTime generatedAt,

		UserContext user,

		/**
		 * 아직 감정 관측 이력이 없다면 null.
		 *
		 * 기존 GET /ai/emotion/latest의 LatestEmotionResponse를
		 * 그대로 재사용합니다.
		 */
		LatestEmotionResponse emotion,

		TravelContext travel,

		QuestContext quests
) {

	/**
	 * AI Manager가 Home에서 필요로 하는 최소 사용자 정보.
	 *
	 * 비밀번호 / 이메일 / 전화번호 등 불필요한 개인정보를
	 * Home 응답에 반복 노출하지 않습니다.
	 */
	public record UserContext(
			Long userId,
			String userName,
			Long totalPoints,
			String grade,
			String profileImageUrl
	) {
	}


	/**
	 * 여행 Context.
	 *
	 * 전체 여행 목록을 모두 Home으로 보내는 대신
	 * Home에서 실제 필요한 개수 + 대표 여행만 내려줍니다.
	 */
	public record TravelContext(
			int totalPlanCount,
			FocusTravel focusPlan
	) {
	}


	/**
	 * AI Manager가 현재 집중해서 볼 여행.
	 *
	 * state:
	 *
	 * ACTIVE   = 현재 여행 기간 중
	 * UPCOMING = 앞으로 시작할 가장 가까운 여행
	 */
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


	/**
	 * 사용자 퀘스트 현황.
	 *
	 * Home에서 모든 Quest Log를 받을 필요는 없으므로
	 * 상태별 count와 최근 완료 이력만 전달합니다.
	 */
	public record QuestContext(
			long activeCount,
			long completedCount,
			long failedCount,
			List<RecentCompletedQuest> recentCompleted
	) {
	}


	/**
	 * AI Activity Feed에 사용할 최근 완료 퀘스트.
	 */
	public record RecentCompletedQuest(
			Long logId,
			Long questId,
			String questName,
			Long rewardPoint,
			LocalDateTime clearDate
	) {
	}
}