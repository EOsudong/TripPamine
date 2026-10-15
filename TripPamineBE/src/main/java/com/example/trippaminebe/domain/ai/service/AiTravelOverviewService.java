
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse;
import com.example.trippaminebe.domain.ai.entity.AiTravelPhase;
import com.example.trippaminebe.domain.travel.dto.TravelPlanResponse;
import com.example.trippaminebe.domain.travel.service.TravelPlanService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * AI 매니저 여행 현황 정리 서비스.
 *
 * 처리 순서:
 *
 * 로그인 사용자 확인
 *       ↓
 * 기존 여행계획 조회
 *       ↓
 * 각 여행의 현재 상태 판단
 *       ↓
 * 출발까지 남은 날짜 계산
 *       ↓
 * 여행 현황 반환
 *
 * DB에 새로운 기록을 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiTravelOverviewService {

	private static final ZoneId SERVICE_ZONE =
			ZoneId.of("Asia/Seoul");

	private final TravelPlanService travelPlanService;

	/**
	 * 현재 사용자의 여행 전체 현황 조회.
	 */
	public AiTravelOverviewResponse getOverview(
			Long userId
	) {
		if (userId == null || userId <= 0) {
			throw new IllegalArgumentException(
					"사용자 정보를 확인해 주세요."
			);
		}

		LocalDateTime now =
				LocalDateTime.now(SERVICE_ZONE);

		/*
		 * 기존 TravelPlanService를 재사용한다.
		 *
		 * 이 기능은 사용자 ID로 조회하며
		 * 삭제되지 않은 여행만 반환한다.
		 *
		 * 다른 사용자의 여행을 조회하지 않는다.
		 */
		List<TravelPlanResponse> travelPlans =
				travelPlanService.findAllByUserId(userId);

		/*
		 * 여행을 하나씩 확인해
		 * 상태와 남은 날짜를 정리한다.
		 */
		List<AiTravelOverviewResponse.TravelItem> plans =
				travelPlans.stream()
						.map(plan -> toTravelItem(plan, now))
						.toList();

		// 상태별 여행 수 계산
		long plannedCount = plans.stream()
				.filter(plan ->
						"PLANNED".equals(plan.phase())
				)
				.count();

		long activeCount = plans.stream()
				.filter(plan ->
						"ACTIVE".equals(plan.phase())
				)
				.count();

		long completedCount = plans.stream()
				.filter(plan ->
						"COMPLETED".equals(plan.phase())
				)
				.count();

		return new AiTravelOverviewResponse(
				now,
				plans.size(),
				plannedCount,
				activeCount,
				completedCount,
				plans
		);
	}

	/**
	 * 기존 여행계획 한 건을
	 * AI 매니저용 여행 정보로 변환한다.
	 */
	private AiTravelOverviewResponse.TravelItem toTravelItem(
			TravelPlanResponse plan,
			LocalDateTime now
	) {
		AiTravelPhase phase = determinePhase(plan, now);

		Long daysUntilStart = null;

		/*
		 * 아직 출발하지 않은 여행에만
		 * 출발까지 남은 날짜를 표시한다.
		 *
		 * 같은 날 출발이라면 0일로 표시한다.
		 */
		if (phase == AiTravelPhase.PLANNED
				&& plan.getStartDate() != null) {

			daysUntilStart = Math.max(
					0L,
					ChronoUnit.DAYS.between(
							now.toLocalDate(),
							plan.getStartDate().toLocalDate()
					)
			);
		}

		return new AiTravelOverviewResponse.TravelItem(
				plan.getPlanId(),
				plan.getPlanName(),
				plan.getLocationCd(),
				plan.getCompanionType(),
				plan.getTotalBudget(),
				plan.getStartDate(),
				plan.getEndDate(),
				phase.name(),
				toKoreanStatus(phase),
				daysUntilStart
		);
	}

	/**
	 * 여행 날짜를 기준으로 현재 상태를 판단한다.
	 *
	 * 종료일이 지났으면 여행 종료.
	 * 시작일이 지났고 종료되지 않았으면 여행 중.
	 * 나머지는 여행 준비 중으로 판단한다.
	 */
	private AiTravelPhase determinePhase(
			TravelPlanResponse plan,
			LocalDateTime now
	) {
		if (plan.getEndDate() != null
				&& plan.getEndDate().isBefore(now)) {
			return AiTravelPhase.COMPLETED;
		}

		if (plan.getStartDate() != null
				&& !plan.getStartDate().isAfter(now)) {
			return AiTravelPhase.ACTIVE;
		}

		return AiTravelPhase.PLANNED;
	}

	/**
	 * 사용자에게 보여줄 상태 문구.
	 */
	private String toKoreanStatus(
			AiTravelPhase phase
	) {
		return switch (phase) {
			case PLANNED -> "여행 준비 중";
			case ACTIVE -> "여행 중";
			case COMPLETED -> "여행 종료";
			case DELETED -> "삭제된 여행";
		};
	}
}
