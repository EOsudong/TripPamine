package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.EmotionPersistenceResult;
import com.example.trippaminebe.domain.ai.dto.EmotionPredictResponse;
import com.example.trippaminebe.domain.ai.entity.AiEmotionObservation;
import com.example.trippaminebe.domain.ai.entity.AiManagerEvent;
import com.example.trippaminebe.domain.ai.entity.AiModelRun;
import com.example.trippaminebe.domain.ai.entity.AiModelRunStatus;
import com.example.trippaminebe.domain.ai.repository.AiEmotionObservationRepository;
import com.example.trippaminebe.domain.ai.repository.AiManagerEventRepository;
import com.example.trippaminebe.domain.ai.repository.AiModelRunRepository;
import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 감정 분석 결과의 DB 저장만 담당하는 Service.
 *
 * 이 클래스에는 FastAPI HTTP 호출을 넣지 않는다.
 *
 * 이유:
 * 외부 HTTP 요청을 기다리는 동안 DB Transaction을 열어두면
 * Connection Pool과 DB Lock을 불필요하게 오래 점유할 수 있다.
 *
 * 따라서:
 *
 * EmotionAnalysisService
 *      ↓ FastAPI 호출
 * EmotionPersistenceService
 *      ↓ @Transactional
 * Oracle 저장
 *
 * 구조로 책임을 분리한다.
 */
@Service
@RequiredArgsConstructor
public class EmotionPersistenceService {

	private static final String MODEL_DOMAIN =
			"EMOTION";

	private static final String MODEL_NAME =
			"KoELECTRA+KcELECTRA";

	private static final String EVENT_TYPE =
			"EMOTION_ANALYZED";

	private static final String SOURCE_TYPE =
			"EMOTION_API";

	private final UserRepository userRepository;

	private final TravelPlanRepository travelPlanRepository;

	private final AiModelRunRepository aiModelRunRepository;

	private final AiManagerEventRepository aiManagerEventRepository;

	private final AiTimeProvider aiTimeProvider;

	private final AiEmotionObservationRepository
			aiEmotionObservationRepository;

	/*
	 * Spring Boot가 이미 JsonMapper Bean을 자동 구성해주므로
	 * 직접 new JsonMapper()를 만들 필요가 없다.
	 *
	 * 이렇게 Spring이 관리하는 Mapper를 사용하면
	 * 애플리케이션 전체의 JSON 설정을 일관되게 유지할 수 있다.
	 */
	private final JsonMapper jsonMapper;

	/**
	 * 성공한 감정 분석 결과 전체를 하나의 DB Transaction으로 저장한다.
	 *
	 * 세 테이블 중 하나라도 INSERT에 실패하면
	 * 세 INSERT 전체가 ROLLBACK 된다.
	 */
	@Transactional
	public EmotionPersistenceResult persistSuccess(
			Long userId,
			Long planId,
			String requestId,
			LocalDateTime startedAt,
			EmotionPredictResponse prediction
	) {

		/*
		 * 로그인 사용자.
		 *
		 * getReferenceById()는 User 전체 SELECT를 즉시 수행하지 않고
		 * FK 연결에 사용할 JPA Proxy를 만든다.
		 *
		 * 이미 Security 인증을 통과한 userId이므로
		 * 여기서는 불필요한 SELECT를 줄일 수 있다.
		 */
		User user =
				userRepository.getReferenceById(
						userId
				);

		/*
		 * 감정 분석은 특정 여행과 무관할 수도 있으므로
		 * PLAN_ID는 nullable이다.
		 */
		TravelPlan travelPlan =
				planId == null
						? null
						: travelPlanRepository
						.getReferenceById(
								planId
						);

		/*
		 * STEP 1.
		 *
		 * 어떤 AI 모델이 어떤 정책으로 실행되었는지를 기록한다.
		 */
		AiModelRun modelRun =
				AiModelRun.builder()
						.user(user)
						.travelPlan(travelPlan)
						.requestId(requestId)
						.modelDomain(MODEL_DOMAIN)
						.modelName(MODEL_NAME)
						.modelVersion(
								prediction.modelVersion()
						)
						.policyVersion(
								prediction.policyVersion()
						)
						.contextMode(
								prediction
										.contextMode()
										.name()
						)
						.inputTurnCount(
								prediction
										.humanTurnCount()
						)
						.turnsUsed(
								prediction.turnsUsed()
						)
						.fallbackTriggered(
								prediction
										.fallbackTriggered()
										? "Y"
										: "N"
						)
						.first2Margin(
								prediction.first2Margin()
						)
						.confidence(
								prediction.confidence()
						)
						.latencyMs(
								prediction.latencyMs()
						)
						.status(
								AiModelRunStatus.SUCCESS
						)
						.startedAt(startedAt)
						.completedAt(
								aiTimeProvider.now()
						)
						.build();

		modelRun =
				aiModelRunRepository.save(
						modelRun
				);

		/*
		 * STEP 2.
		 *
		 * AI Manager 관점에서 "감정이 분석되었다"라는
		 * 도메인 이벤트를 기록한다.
		 *
		 * 중요:
		 * 사용자가 입력한 대화 원문은 여기 저장하지 않는다.
		 *
		 * 원문 복제를 피하고,
		 * 모델 실행에 필요한 메타데이터만 JSON으로 보관한다.
		 */
		AiManagerEvent event =
				AiManagerEvent.builder()
						.user(user)
						.travelPlan(travelPlan)
						.modelRun(modelRun)
						.eventType(EVENT_TYPE)
						.sourceType(SOURCE_TYPE)
						.sourceId(requestId)
						.dedupeKey(
								"EMOTION:" + requestId
						)
						.payloadJson(
								buildEventPayload(
										prediction
								)
						)

						// 이벤트 발생 시각도 Asia/Seoul 기준으로 저장
						.occurredAt(
								aiTimeProvider.now()
						)

						.build();

		event =
				aiManagerEventRepository.save(
						event
				);

		/*
		 * STEP 3.
		 *
		 * 실제 사용자 감정 관측값을 저장한다.
		 *
		 * TOP_K_JSON은 현재 Q17 Runtime이 top-k 목록을
		 * 반환하지 않으므로 NULL로 둔다.
		 */
		AiEmotionObservation observation =
				AiEmotionObservation.builder()
						.user(user)
						.travelPlan(travelPlan)
						.event(event)
						.modelRun(modelRun)

						.fineEmotion(
								prediction.fineLabel()
						)

						.fineEmotionName(
								prediction.fineLabelName()
						)

						.coarseEmotion(
								prediction.coarseLabel()
						)

						.confidence(
								prediction.confidence()
						)

						.topKJson(null)

						/*
						 * Oracle SYSTIMESTAMP가 아닌
						 * 서비스 기준 Asia/Seoul 시각을 직접 저장한다.
						 */
						.observedAt(
								aiTimeProvider.now()
						)

						.build();

		observation =
				aiEmotionObservationRepository.save(
						observation
				);

		return new EmotionPersistenceResult(
				modelRun.getModelRunId(),
				event.getEventId(),
				observation.getObservationId()
		);
	}

	/**
	 * 실패한 AI 모델 실행을 감사(Audit) 목적으로 기록한다.
	 *
	 * REQUIRES_NEW를 사용하는 이유:
	 *
	 * EmotionAnalysisService에서 AI 예외를 다시 밖으로 던지더라도
	 * 실패 기록 자체는 독립 Transaction으로 COMMIT되어야 한다.
	 *
	 * 즉 사용자 API는 503/422 등으로 실패하더라도
	 * 운영자는 DB에서 "언제, 어떤 요청이, 어떤 이유로 실패했는지"
	 * 확인할 수 있다.
	 *
	 * 실패 시에는 실제 감정 결과가 없으므로:
	 *
	 * AI_MODEL_RUNS             생성
	 * AI_MANAGER_EVENTS         생성하지 않음
	 * AI_EMOTION_OBSERVATIONS   생성하지 않음
	 */
	@Transactional(
			propagation = Propagation.REQUIRES_NEW
	)
	public Long persistFailure(
			Long userId,
			Long planId,
			String requestId,
			LocalDateTime startedAt,
			int inputTurnCount,
			BigDecimal latencyMs,
			String errorCode,
			String errorMessage
	) {

		/*
		 * 로그인된 사용자를 FK로 연결한다.
		 *
		 * getReferenceById()는 즉시 전체 User를 조회하기보다
		 * JPA Proxy를 이용하므로 단순 FK 연결에 적합하다.
		 */
		User user =
				userRepository.getReferenceById(
						userId
				);

		/*
		 * 특정 여행과 관계없는 감정 분석일 수도 있으므로
		 * PLAN_ID는 null을 허용한다.
		 */
		TravelPlan travelPlan =
				planId == null
						? null
						: travelPlanRepository
						.getReferenceById(
								planId
						);

		/*
		 * FastAPI 응답을 받지 못했기 때문에
		 * 실제 modelVersion/policyVersion은 확인할 수 없다.
		 *
		 * MODEL_VERSION은 DB NOT NULL 컬럼이므로
		 * 추측한 버전을 넣지 않고 명시적으로 UNAVAILABLE을 기록한다.
		 */
		AiModelRun failedRun =
				AiModelRun.builder()
						.user(user)
						.travelPlan(travelPlan)

						.requestId(
								requestId
						)

						.modelDomain(
								MODEL_DOMAIN
						)

						.modelName(
								MODEL_NAME
						)

						.modelVersion(
								"UNAVAILABLE"
						)

						/*
						 * 응답 자체를 받지 못했으므로
						 * 실제 정책 버전도 확정할 수 없다.
						 */
						.policyVersion(null)

						.contextMode(null)

						/*
						 * 요청에 들어온 전체 Human Turn 개수는
						 * Spring Boot 단계에서 이미 알고 있으므로 기록 가능하다.
						 */
						.inputTurnCount(
								inputTurnCount
						)

						/*
						 * 실제 모델이 몇 Turn을 사용했는지는
						 * FastAPI가 실패했으므로 알 수 없다.
						 */
						.turnsUsed(null)

						/*
						 * fallback 수행 여부 역시 알 수 없으므로
						 * 기본값 N으로 기록한다.
						 *
						 * 이것은 "fallback이 실행되지 않았다"는 모델 판단이 아니라
						 * "확인 가능한 fallback 성공 결과가 없다"는 의미다.
						 */
						.fallbackTriggered(
								"N"
						)

						.first2Margin(null)
						.confidence(null)

						/*
						 * Spring Boot에서 관찰한
						 * 전체 외부 호출 경과시간.
						 */
						.latencyMs(
								latencyMs
						)

						.status(
								AiModelRunStatus.FAILED
						)

						/*
						 * DB 컬럼 길이를 넘는 예외 메시지가 들어오지 않도록
						 * 아래 helper에서 길이를 제한한다.
						 */
						.errorCode(
								truncate(
										errorCode,
										100
								)
						)

						.errorMessage(
								truncate(
										errorMessage,
										1000
								)
						)

						.startedAt(
								startedAt
						)

						.completedAt(
								aiTimeProvider.now()
						)

						.build();

		failedRun =
				aiModelRunRepository.save(
						failedRun
				);

		return failedRun.getModelRunId();
	}

	/**
	 * AI_MANAGER_EVENTS.PAYLOAD_JSON에 저장할
	 * 감정 분석 실행 부가정보를 JSON으로 만든다.
	 *
	 * 핵심 원칙:
	 *
	 * 1. 사용자가 입력한 실제 대화 원문은 저장하지 않는다.
	 * 2. 모델 실행을 재현하거나 분석하는 데 필요한
	 *    메타데이터만 저장한다.
	 * 3. Spring Boot 4의 기본 JSON 엔진인
	 *    Jackson 3 JsonMapper를 사용한다.
	 *
	 * 예:
	 *
	 * {
	 *   "first2Confidence": 0.86,
	 *   "first2Margin": 0.80,
	 *   "contextMode": "FIRST2",
	 *   "fallbackTriggered": false,
	 *   "humanTurnCount": 3,
	 *   "turnsUsed": 2,
	 *   "policyVersion": "...",
	 *   "modelVersion": "..."
	 * }
	 */
	private String buildEventPayload(
			EmotionPredictResponse prediction
	) {

		Map<String, Object> payload =
				new LinkedHashMap<>();

		/*
		 * 처음 두 발화만 분석했을 때의 confidence.
		 *
		 * 현재 AI_MODEL_RUNS에는 이 값을 위한 별도 컬럼이 없으므로
		 * Event JSON에 보존한다.
		 */
		payload.put(
				"first2Confidence",
				prediction.first2Confidence()
		);

		/*
		 * FIRST2 분석에서 1위 감정과 2위 감정의 확률 차이.
		 *
		 * 이 값이 Q17 정책의 threshold 0.16 이하이고
		 * 추가 대화가 존재하면 FULL_FALLBACK이 실행된다.
		 */
		payload.put(
				"first2Margin",
				prediction.first2Margin()
		);

		// FIRST2 또는 FULL_FALLBACK
		payload.put(
				"contextMode",
				prediction.contextMode()
		);

		// 실제 fallback이 발생했는지 여부
		payload.put(
				"fallbackTriggered",
				prediction.fallbackTriggered()
		);

		// 전체 사용자 발화 개수
		payload.put(
				"humanTurnCount",
				prediction.humanTurnCount()
		);

		// 실제 모델 추론에 사용된 발화 개수
		payload.put(
				"turnsUsed",
				prediction.turnsUsed()
		);

		// Q17 Locked Policy 버전
		payload.put(
				"policyVersion",
				prediction.policyVersion()
		);

		// KoELECTRA + KcELECTRA Ensemble 모델 버전
		payload.put(
				"modelVersion",
				prediction.modelVersion()
		);

		try {

			/*
			 * Java Map 객체를 Oracle CLOB에 저장할
			 * JSON 문자열로 직렬화한다.
			 *
			 * JsonMapper는 Spring Boot가 자동 구성한 Bean이므로
			 * Spring MVC가 사용하는 JSON 정책과 같은 계열의
			 * 설정을 사용할 수 있다.
			 */
			return jsonMapper
					.writeValueAsString(
							payload
					);

		} catch (
				JacksonException exception
		) {

			/*
			 * JSON 생성에 실패하면 Event만 빼고 저장하는 대신
			 * 전체 Transaction을 실패시킨다.
			 *
			 * 이유:
			 *
			 * AI_MODEL_RUNS          저장됨
			 * AI_MANAGER_EVENTS      실패
			 * AI_EMOTION_OBSERVATIONS ?
			 *
			 * 같은 불완전한 상태를 DB에 남기지 않기 위해서다.
			 *
			 * RuntimeException으로 변환하면 @Transactional이
			 * 전체 INSERT를 rollback한다.
			 */
			throw new IllegalStateException(
					"감정 분석 이벤트 JSON 생성에 실패했습니다.",
					exception
			);
		}
	}

	/**
	 * DB VARCHAR2 컬럼 길이를 초과하는 문자열을 안전하게 잘라낸다.
	 *
	 * 외부 API 오류 메시지는 라이브러리/서버 상태에 따라
	 * 예상보다 길어질 수 있으므로 DB 저장 직전에 방어한다.
	 */
	private String truncate(
			String value,
			int maxLength
	) {

		if (value == null) {
			return null;
		}

		if (value.length() <= maxLength) {
			return value;
		}

		return value.substring(
				0,
				maxLength
		);
	}
}