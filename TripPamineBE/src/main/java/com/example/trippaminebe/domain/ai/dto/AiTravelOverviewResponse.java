
package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 매니저가 사용할 여행 현황.
 *
 * 기존 여행계획을 다시 저장하지 않고
 * 화면과 AI 매니저에서 필요한 정보만 정리한다.
 *
 * 사용자에게는 "여행 준비 중", "여행 중" 등
 * 이해하기 쉬운 문구를 제공한다.
 */
public record AiTravelOverviewResponse(

		// 여행 정보 확인 시각
		LocalDateTime generatedAt,

		// 사용자의 전체 여행 수
		int totalPlanCount,

		// 예정된 여행 수
		long plannedCount,

		// 현재 진행 중인 여행 수
		long activeCount,

		// 종료된 여행 수
		long completedCount,

		// 여행별 상세 현황
		List<TravelItem> plans

) {

	/**
	 * 여행 계획 한 건.
	 *
	 * 지출 금액을 임의로 계산하지 않는다.
	 * totalBudget은 사용자가 설정한 여행 예산이다.
	 */
	public record TravelItem(

			Long planId,

			String planName,

			String locationCd,

			String companionType,

			BigDecimal totalBudget,

			LocalDateTime startDate,

			LocalDateTime endDate,

			// 내부 상태: PLANNED / ACTIVE / COMPLETED
			String phase,

			// 사용자 화면용 한글 상태
			String statusLabel,

			// 출발까지 남은 일수
			Long daysUntilStart
	) {
	}
}