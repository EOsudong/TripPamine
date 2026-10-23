package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.client.EmotionApiClient;
import com.example.trippaminebe.domain.ai.client.EmotionApiException;
import com.example.trippaminebe.domain.ai.dto.EmotionAnalysisResponse;
import com.example.trippaminebe.domain.ai.dto.EmotionPersistenceResult;
import com.example.trippaminebe.domain.ai.dto.EmotionPredictRequest;
import com.example.trippaminebe.domain.ai.dto.EmotionPredictResponse;
import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 감정 분석 전체 내역을 조정하는 Application Service.
 *
 * 역할:
 *
 * 1. 요청 사용자가 해당 여행의 소유자인지 확인
 * 2. Python FastAPI 감정 모델 호출
 * 3. 모델 결과를 Oracle에 Transaction 저장
 * 4. 최종 API 응답 조립
 *
 * 외부 AI 호출과 DB 저장을 분리하는 것이 핵심이다.
 */
@Service
@RequiredArgsConstructor
public class EmotionAnalysisService {

	private final EmotionApiClient emotionApiClient;

	private final EmotionPersistenceService
			emotionPersistenceService;

	private final TravelPlanRepository
			travelPlanRepository;

	private final AiTimeProvider aiTimeProvider;

	/**
	 * 로그인 사용자의 감정을 분석한다.
	 *
	 * 전체 처리 순서:
	 *
	 * 1. 여행 소유권 검증
	 * 2. requestId 생성
	 * 3. FastAPI 호출 시작 시각 기록
	 * 4. FastAPI 호출
	 *
	 * 성공:
	 *   AI_MODEL_RUNS + AI_MANAGER_EVENTS
	 *   + AI_EMOTION_OBSERVATIONS 저장
	 *
	 * 실패:
	 *   AI_MODEL_RUNS에 FAILED 기록 후
	 *   원래 EmotionApiException을 다시 호출자에게 전달
	 */
	public EmotionAnalysisResponse analyze(
			Long userId,
			EmotionPredictRequest request
	) {
		// 기존 단독 분석 API는 예측 후 즉시 DB 저장합니다.
		// 채팅만 예측과 저장을 나눠, 답변 생성 전에 모델 결과를 참고합니다.
		PreparedChatEmotion prepared = prepareChatEmotion(userId, request);
		return persistPreparedChatEmotion(userId, request.planId(), prepared);
	}

	/**
	 * 채팅에서는 AI 답변 전에 감정을 예측하고, 성공 저장은 뒤로 미룹니다.
	 */
	public PreparedChatEmotion prepareChatEmotion(Long userId, EmotionPredictRequest request) {

		/*
		 * 존재하지 않거나 다른 사용자의 PLAN_ID를
		 * AI 데이터에 연결하지 못하도록 먼저 검증한다.
		 *
		 * 이 단계에서 실패한 것은 "AI 모델 실행 실패"가 아니므로
		 * AI_MODEL_RUNS에 실패 기록을 만들지 않는다.
		 */
		validatePlanOwnership(
				userId,
				request.planId()
		);

		/*
		 * 한 번의 AI 실행을 추적하는 Correlation ID.
		 *
		 * Spring 로그와 DB ModelRun을 같은 ID로 연결할 수 있다.
		 */
		String requestId =
				UUID.randomUUID()
						.toString();

		/*
		 * AI 실행 시작 시각도 시스템 기본 timezone에 의존하지 않고
		 * 서비스 기준인 Asia/Seoul을 사용한다.
		 */
		LocalDateTime startedAt =
				aiTimeProvider.now();

		/*
		 * LocalDateTime은 사람이 읽기 좋은 실제 시각 기록용이고,
		 * nanoTime은 정확한 경과시간 측정용이다.
		 *
		 * System.currentTimeMillis()보다 nanoTime()이
		 * elapsed time 측정에 적합하다.
		 */
		long startedNano =
				System.nanoTime();

		try {

			/*
			 * Python FastAPI 호출.
			 *
			 * 아직 DB Transaction은 열리지 않은 상태다.
			 */
			EmotionPredictResponse prediction =
					emotionApiClient.predict(
							request.humanTurns()
					);

			// 감정 예측을 잠시 보관하고, 채팅 답변 생성 후 성공 기록을 남깁니다.
			return new PreparedChatEmotion(requestId, startedAt, prediction);

		} catch (
				EmotionApiException exception
		) {

			/*
			 * FastAPI HTTP 오류나 연결 실패도
			 * 운영 관점에서는 중요한 모델 실행 이력이다.
			 */
			BigDecimal failureLatencyMs =
					elapsedMilliseconds(
							startedNano
					);

			/*
			 * 현재 EmotionApiException은 upstreamStatus를 가지고 있다.
			 *
			 * 예:
			 *
			 * HTTP 422
			 * → EMOTION_API_422
			 *
			 * HTTP 503 또는 연결 실패
			 * → EMOTION_API_503
			 */
			String errorCode =
					buildErrorCode(
							exception
									.getUpstreamStatus()
					);

			/*
			 * 실패 ModelRun을 별도 Transaction으로 저장한다.
			 *
			 * 이후 같은 exception을 다시 throw해도
			 * REQUIRES_NEW이므로 실패 이력은 유지된다.
			 */
			emotionPersistenceService
					.persistFailure(
							userId,
							request.planId(),
							requestId,
							startedAt,
							request
									.humanTurns()
									.size(),
							failureLatencyMs,
							errorCode,
							exception.getMessage()
					);

			/*
			 * 기존 EmotionApiExceptionHandler가
			 * 이 예외를 422/503/502 HTTP 응답으로 변환하도록
			 * 원래 예외를 그대로 다시 던진다.
			 */
			throw exception;
		}
	}

	/**
	 * Qwen3 답변이 정상 생성되고 채팅 저장 단계가 끝난 뒤에만 감정 성공 기록을 보존한다.
	 * 감정 분석 동의나 사용자 식별은 호출측 서비스에서 이미 검증한다.
	 */
	public EmotionAnalysisResponse persistPreparedChatEmotion(
			Long userId,
			Long planId,
			PreparedChatEmotion prepared
	) {
		EmotionPersistenceResult persistence = emotionPersistenceService.persistSuccess(
				userId, planId, prepared.requestId(), prepared.startedAt(), prepared.prediction()
		);
		return EmotionAnalysisResponse.from(prepared.requestId(), prepared.prediction(), persistence);
	}

	/**
	 * 추론 성공과 DB 성공 저장 사이에 잠시 전달하는 값이다.
	 * 원문 사용자 발화는 담지 않고 모델 결과·추적 ID·시작 시각만 담는다.
	 */
	public record PreparedChatEmotion(
			String requestId,
			LocalDateTime startedAt,
			EmotionPredictResponse prediction
	) {
	}


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

			throw new IllegalArgumentException(
					"해당 여행 계획을 찾을 수 없거나 "
							+ "현재 사용자의 여행 계획이 아닙니다."
			);
		}
	}

	/**
	 * FastAPI 호출에 걸린 실제 경과시간을 millisecond 단위로 계산한다.
	 *
	 * DB의 LATENCY_MS NUMBER(12,3)에 맞춰
	 * 소수점 셋째 자리까지 보존한다.
	 */
	private BigDecimal elapsedMilliseconds(
			long startedNano
	) {

		long elapsedNano =
				System.nanoTime()
						- startedNano;

		double milliseconds =
				elapsedNano
						/ 1_000_000.0;

		return BigDecimal
				.valueOf(
						milliseconds
				)
				.setScale(
						3,
						RoundingMode.HALF_UP
				);
	}


	/**
	 * 외부 Emotion API HTTP 상태를
	 * 운영 검색이 쉬운 Error Code로 변환한다.
	 *
	 * 예:
	 *
	 * EMOTION_API_422
	 * EMOTION_API_503
	 * EMOTION_API_ERROR
	 */
	private String buildErrorCode(
			Integer upstreamStatus
	) {

		if (upstreamStatus == null) {

			return "EMOTION_API_ERROR";
		}

		return "EMOTION_API_"
				+ upstreamStatus;
	}
}