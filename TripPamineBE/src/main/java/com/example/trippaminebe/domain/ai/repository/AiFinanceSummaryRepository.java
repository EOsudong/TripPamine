
package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiFinancePeriodType;
import com.example.trippaminebe.domain.ai.entity.AiFinanceSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * AI 금융 요약 저장소.
 *
 * 역할:
 * AI_FINANCE_SUMMARIES 테이블에서
 * 사용자의 금융 요약을 찾거나 저장할 때 사용한다.
 *
 * 실제 저장 작업은 Service에서 결정한다.
 */
public interface AiFinanceSummaryRepository
		extends JpaRepository<AiFinanceSummary, Long> {

	// 기존 기능 1: 사용자의 가장 최근 기간별 요약 조회
	Optional<AiFinanceSummary>
	findTopByUser_IdAndPeriodTypeOrderByAsOfAtDesc(
			Long userId,
			AiFinancePeriodType periodType
	);

	// 기존 기능 2: 특정 여행의 최근 금융 요약 조회
	Optional<AiFinanceSummary>
	findTopByTravelPlan_PlanIdOrderByAsOfAtDesc(
			Long planId
	);

	/**
	 * 신규 기능: 특정 사용자의 특정 월 금융 요약 조회.
	 *
	 * 조건:
	 * 1. 사용자 ID 일치
	 * 2. 기간 종류 일치
	 * 3. 시작일과 종료일 일치
	 * 4. 특정 여행에 속하지 않는 일반 월별 요약
	 *
	 * 동일한 조건으로 여러 기록이 있으면
	 * 가장 최근에 생성된 ID의 기록을 가져온다.
	 *
	 * 다른 사용자의 금융 정보가 섞이지 않도록
	 * 반드시 사용자 ID를 조건에 포함한다.
	 */
	Optional<AiFinanceSummary>
	findFirstByUser_IdAndPeriodTypeAndPeriodStartAndPeriodEndAndTravelPlanIsNullOrderBySummaryIdDesc(
			Long userId,
			AiFinancePeriodType periodType,
			LocalDateTime periodStart,
			LocalDateTime periodEnd
	);
}
