package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.LatestEmotionResponse;
import com.example.trippaminebe.domain.ai.entity.AiEmotionObservation;
import com.example.trippaminebe.domain.ai.entity.AiManagerEvent;
import com.example.trippaminebe.domain.ai.entity.AiModelRun;
import com.example.trippaminebe.domain.ai.repository.AiEmotionObservationRepository;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * AI 감정 데이터의 "조회"만 담당하는 Service.
 *
 * 쓰기:
 * EmotionPersistenceService
 *
 * 읽기:
 * EmotionQueryService
 *
 * 이렇게 읽기/쓰기를 분리하면
 * 클래스별 책임이 명확해지고 이후 AI Manager Home을 조립하기도 쉬워진다.
 */
@Service
@RequiredArgsConstructor
public class EmotionQueryService {

	private final AiEmotionObservationRepository
			aiEmotionObservationRepository;

	private final TravelPlanRepository
			travelPlanRepository;

	/**
	 * 로그인 사용자의 가장 최근 감정 상태를 조회한다.
	 *
	 * planId == null
	 * → 사용자의 모든 감정 중 최신
	 *
	 * planId != null
	 * → 해당 여행에서 발생한 감정 중 최신
	 */
	@Transactional(readOnly = true)
	public Optional<LatestEmotionResponse> findLatest(
			Long userId,
			Long planId
	) {

		/*
		 * 특정 여행 기준으로 조회할 경우
		 * 먼저 이 여행이 현재 로그인 사용자의 것인지 확인한다.
		 */
		validatePlanOwnership(
				userId,
				planId
		);

		Optional<AiEmotionObservation> observation;

		if (planId == null) {

			/*
			 * 여행 구분 없이
			 * 사용자의 전체 감정 중 최신값을 조회한다.
			 */
			observation =
					aiEmotionObservationRepository
							.findTopByUser_IdOrderByObservedAtDesc(
									userId
							);

		} else {

			/*
			 * 로그인 사용자 + PLAN_ID를 같이 조건으로 사용한다.
			 *
			 * 보안상 planId 단독 조회보다 안전하다.
			 */
			observation =
					aiEmotionObservationRepository
							.findTopByUser_IdAndTravelPlan_PlanIdOrderByObservedAtDesc(
									userId,
									planId
							);
		}

		/*
		 * Optional.empty()이면
		 * 아직 사용자에게 감정 분석 이력이 없다는 의미다.
		 */
		return observation.map(
				this::toResponse
		);
	}

	/**
	 * JPA Entity를 외부 API DTO로 변환한다.
	 *
	 * Entity 자체를 Controller에서 반환하지 않는 이유:
	 *
	 * 1. Lazy Loading 문제 방지
	 * 2. DB 구조가 API 계약으로 노출되는 것 방지
	 * 3. 필요한 필드만 명확하게 전달
	 */
	private LatestEmotionResponse toResponse(
			AiEmotionObservation observation
	) {

		AiModelRun modelRun =
				observation.getModelRun();

		AiManagerEvent event =
				observation.getEvent();

		Long planId =
				observation.getTravelPlan() == null
						? null
						: observation
						.getTravelPlan()
						.getPlanId();

		/*
		 * DB에는 fallback 여부를 CHAR(1) Y/N으로 저장한다.
		 *
		 * 외부 API에서는 boolean이 더 자연스러우므로
		 * 여기서 변환한다.
		 */
		boolean fallbackTriggered =
				"Y".equalsIgnoreCase(
						modelRun.getFallbackTriggered()
				);

		return new LatestEmotionResponse(

				observation.getObservationId(),

				event == null
						? null
						: event.getEventId(),

				modelRun.getModelRunId(),

				modelRun.getRequestId(),

				planId,

				observation.getFineEmotion(),

				observation.getFineEmotionName(),

				observation.getCoarseEmotion(),

				observation.getConfidence(),

				modelRun.getModelVersion(),

				modelRun.getPolicyVersion(),

				modelRun.getContextMode(),

				fallbackTriggered,

				observation.getObservedAt()
		);
	}

	/**
	 * 요청한 PLAN_ID가 현재 사용자의 여행인지 검증한다.
	 *
	 * planId가 없는 일반 감정 조회라면 검증할 필요가 없다.
	 */
	private void validatePlanOwnership(
			Long userId,
			Long planId
	) {

		if (planId == null) {
			return;
		}

		boolean owned =
				travelPlanRepository
						.findByPlanIdAndUser_IdAndDelYn(
								planId,
								userId,
								"N"
						)
						.isPresent();

		if (!owned) {

			/*
			 * 존재 여부와 소유권 여부를 외부에 구분해서 알려주지 않는다.
			 *
			 * 이를 통해 다른 사용자의 PLAN_ID가 실제 존재하는지
			 * 추측하기 어렵게 한다.
			 */
			throw new IllegalArgumentException(
					"해당 여행 계획을 찾을 수 없거나 "
							+ "현재 사용자의 여행 계획이 아닙니다."
			);
		}
	}
}