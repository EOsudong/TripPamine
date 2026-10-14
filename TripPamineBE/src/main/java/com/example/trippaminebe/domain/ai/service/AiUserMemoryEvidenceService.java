package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.entity.AiManagerEvent;
import com.example.trippaminebe.domain.ai.entity.AiUserFact;
import com.example.trippaminebe.domain.ai.entity.AiUserFactEvidence;
import com.example.trippaminebe.domain.ai.entity.AiUserFactSourceType;
import com.example.trippaminebe.domain.ai.entity.AiUserFactStatus;
import com.example.trippaminebe.domain.ai.repository.AiManagerEventRepository;
import com.example.trippaminebe.domain.ai.repository.AiUserFactEvidenceRepository;
import com.example.trippaminebe.domain.ai.repository.AiUserFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;


/**
 * AI Manager Event를
 * 장기 사용자 Memory의 Evidence로 연결하는 Service.
 *
 *
 * =========================================================
 * STEP D-2A의 중요한 원칙
 * =========================================================
 *
 * 이번 단계에서는 "선호"를 추론하지 않는다.
 *
 *
 * 예:
 *
 * 여행 계획 1건:
 *
 * companionType = ALONE
 *
 *
 * 이것만 보고:
 *
 * "사용자는 혼자 여행하는 것을 선호한다"
 *
 * 라고 판단하면 안 된다.
 *
 *
 * 대신:
 *
 * "사용자가 ALONE 형태의 여행을 계획한 적이 있다"
 *
 * 라는 직접 관찰 사실만 저장한다.
 *
 *
 * 이후 여러 Evidence가 누적되면:
 *
 * AI_USER_FACTS
 *      ↓
 * Preference / Pattern Inference
 *
 * 단계에서 별도의 추론을 수행하게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUserMemoryEvidenceService {

	private static final BigDecimal
			DIRECT_OBSERVATION_CONFIDENCE =
			BigDecimal.ONE;


	private final AiManagerEventRepository
			aiManagerEventRepository;

	private final AiUserFactRepository
			aiUserFactRepository;

	private final AiUserFactEvidenceRepository
			aiUserFactEvidenceRepository;

	private final JsonMapper
			jsonMapper;


	/**
	 * AI_MANAGER_EVENTS 한 건을 Memory Evidence로 반영한다.
	 *
	 *
	 * 현재 STEP D-2A에서 지원하는 Event:
	 *
	 * TRAVEL_PLAN_CREATED
	 *
	 *
	 * UPDATE를 제외하는 이유:
	 *
	 * 같은 여행 계획을 여러 번 수정할 경우
	 * 같은 행동이 Evidence Count에 반복 반영될 수 있기 때문이다.
	 *
	 * 우선 여행 계획 "생성"이라는 명확한 행동만
	 * Memory Evidence로 사용한다.
	 *
	 *
	 * @return 새로 생성된 Evidence 개수
	 */
	@Transactional(
			propagation = Propagation.REQUIRES_NEW
	)
	public int captureEvidence(
			Long eventId
	) {

		if (eventId == null) {
			return 0;
		}


		/*
		 * =================================================
		 * 1. AI Manager Event 조회
		 * =================================================
		 */

		AiManagerEvent event =
				aiManagerEventRepository
						.findById(
								eventId
						)
						.orElse(null);


		if (event == null) {

			log.warn(
					"[AI-MEMORY] Event를 찾을 수 없습니다. eventId={}",
					eventId
			);

			return 0;
		}


		/*
		 * =================================================
		 * 2. 현재 D-2A 대상 Event 필터링
		 * =================================================
		 *
		 * 아직 Preference 추론 단계가 아니므로
		 * TRAVEL_PLAN_CREATED만 사용한다.
		 */
		if (
				!"TRAVEL_PLAN_CREATED"
						.equalsIgnoreCase(
								event.getEventType()
						)
		) {

			return 0;
		}


		/*
		 * =================================================
		 * 3. Event Snapshot JSON 읽기
		 * =================================================
		 */

		JsonNode payload =
				readPayload(
						event.getPayloadJson()
				);


		int createdEvidenceCount = 0;


		/*
		 * =================================================
		 * 4. 여행 동반 형태 관찰
		 * =================================================
		 *
		 * 예:
		 *
		 * companionType = ALONE
		 *
		 * ↓
		 *
		 * FACT_KEY:
		 *
		 * travel.observed.companion_type.alone
		 *
		 *
		 * 여기서 observed가 매우 중요하다.
		 *
		 * 이것은:
		 *
		 * "사용자는 ALONE을 선호한다"
		 *
		 * 가 아니라:
		 *
		 * "ALONE 여행 계획이 관찰됐다"
		 *
		 * 라는 의미다.
		 */
		String companionType =
				readText(
						payload,
						"companionType"
				);


		if (
				companionType != null
		) {

			createdEvidenceCount +=
					addObservedAttribute(

							event,

							"travel",

							"companion_type",

							companionType
					);
		}


		/*
		 * =================================================
		 * 5. 여행 지역 관찰
		 * =================================================
		 *
		 * 예:
		 *
		 * locationCd = JEJU
		 *
		 * ↓
		 *
		 * travel.observed.location.jeju
		 */
		String locationCd =
				readText(
						payload,
						"locationCd"
				);


		if (
				locationCd != null
		) {

			createdEvidenceCount +=
					addObservedAttribute(

							event,

							"travel",

							"location",

							locationCd
					);
		}

		/*
		 * =================================================
		 * 6. Pattern Candidate 갱신
		 * =================================================
		 *
		 * 새로운 관찰 Evidence가 실제로 추가됐을 때만
		 * 사용자의 Travel Pattern을 다시 계산한다.
		 *
		 * 여기서는 아직 Preference를 확정하지 않는다.
		 *
		 * observed
		 *      ↓
		 * repeated evidence
		 *      ↓
		 * pattern candidate
		 */

		log.info(
				"[AI-MEMORY] Event Evidence 처리 완료. "
						+ "eventId={}, eventType={}, createdEvidence={}",
				event.getEventId(),
				event.getEventType(),
				createdEvidenceCount
		);


		return createdEvidenceCount;
	}


	/**
	 * 관찰된 Attribute 하나를
	 * AI_USER_FACTS + AI_USER_FACT_EVIDENCE에 연결한다.
	 *
	 *
	 * 예:
	 *
	 * domain    = travel
	 * attribute = companion_type
	 * value     = ALONE
	 *
	 *
	 * FACT_KEY:
	 *
	 * travel.observed.companion_type.alone
	 */
	private int addObservedAttribute(
			AiManagerEvent event,
			String domain,
			String attribute,
			String value
	) {

		String normalizedValue =
				normalizeKeyComponent(
						value
				);


		if (
				normalizedValue == null
		) {

			return 0;
		}


		String factKey =
				domain
						+ ".observed."
						+ attribute
						+ "."
						+ normalizedValue;


		/*
		 * =================================================
		 * 1. 기존 Fact 조회
		 * =================================================
		 */

		AiUserFact fact =
				aiUserFactRepository
						.findByUser_IdAndFactKey(

								event
										.getUser()
										.getId(),

								factKey
						)
						.orElse(null);

		boolean newlyCreatedFact =
				false;

		/*
		 * =================================================
		 * 2. 없으면 Fact 생성
		 * =================================================
		 */

		if (
				fact == null
		) {

			Map<String, Object> valueJson =
					new LinkedHashMap<>();


			valueJson.put(
					"domain",
					domain
			);

			valueJson.put(
					"kind",
					"OBSERVED_ATTRIBUTE"
			);

			valueJson.put(
					"attribute",
					attribute
			);

			valueJson.put(
					"value",
					value
			);


			fact =
					AiUserFact
							.builder()

							.user(
									event.getUser()
							)

							.factKey(
									factKey
							)

							.factValueJson(
									toJson(
											valueJson
									)
							)

							/*
							 * Event에서 시스템이 추출한 Fact이므로
							 * USER_DECLARED가 아니라 SYSTEM_INFERRED.
							 *
							 * 단,
							 * 의미 자체는 Preference 추론이 아니라
							 * 직접 관찰 Fact이다.
							 */
							.sourceType(
									AiUserFactSourceType
											.SYSTEM_INFERRED
							)

							/*
							 * 직접 Event Snapshot에서 읽은 값이므로
							 * "관찰 사실 자체"의 신뢰도는 1.0.
							 *
							 * 이것을 Preference Confidence와
							 * 혼동하면 안 된다.
							 */
							.confidence(
									DIRECT_OBSERVATION_CONFIDENCE
							)

							.evidenceCount(
									1
							)

							.status(
									AiUserFactStatus.ACTIVE
							)

							.build();


			fact =
					aiUserFactRepository
							.saveAndFlush(
									fact
							);

			newlyCreatedFact =
					true;
		}


		/*
		 * =================================================
		 * 3. 같은 Event가 이미 Evidence인지 확인
		 * =================================================
		 *
		 * AI_USER_FACT_EVIDENCE의 Unique Constraint도 있지만
		 * INSERT 전에 Application Level에서 한 번 더 방어한다.
		 */
		boolean alreadyExists =
				aiUserFactEvidenceRepository
						.existsByFact_FactIdAndEvent_EventId(

								fact.getFactId(),

								event.getEventId()
						);


		if (
				alreadyExists
		) {

			return 0;
		}


		/*
		 * =================================================
		 * 4. Evidence 생성
		 * =================================================
		 */

		AiUserFactEvidence evidence =
				AiUserFactEvidence
						.builder()

						.fact(
								fact
						)

						.event(
								event
						)

						/*
						 * 직접 사용자 행동 Event이므로
						 * Evidence Weight는 현재 1.0.
						 *
						 * 향후:
						 *
						 * USER_DECLARED
						 * 반복 행동
						 * 오래된 행동
						 *
						 * 등에 따라 Weight 정책을 확장할 수 있다.
						 */
						.weight(
								BigDecimal.ONE
						)

						.build();


		aiUserFactEvidenceRepository
				.saveAndFlush(
						evidence
				);

		/*
		 * 신규 Fact는 INSERT 시 이미
		 * 첫 Evidence를 포함해서 EVIDENCE_COUNT=1로 저장
		 *
		 * 여기서 다시 incrementEvidenceCount()를 호출하면
		 * INSERT 직후 Oracle 시간과 Java 시간이 경쟁하면서
		 * CK_AUF_OBSERVED_TIME을 위반할 수 있다.
		 *
		 * 기존 Fact일 때만 +1 한다.
		 */
		if (
				!newlyCreatedFact
		) {

			fact.incrementEvidenceCount();


			aiUserFactRepository
					.saveAndFlush(
							fact
					);
		}


		log.info(
				"[AI-MEMORY] Evidence 생성. "
						+ "userId={}, factKey={}, eventId={}, evidenceCount={}",
				event.getUser().getId(),
				fact.getFactKey(),
				event.getEventId(),
				fact.getEvidenceCount()
		);


		return 1;
	}


	/**
	 * PAYLOAD_JSON 안전 파싱.
	 */
	private JsonNode readPayload(
			String payloadJson
	) {

		if (
				payloadJson == null
						||
						payloadJson.isBlank()
		) {

			return jsonMapper
					.createObjectNode();
		}


		try {

			return jsonMapper
					.readTree(
							payloadJson
					);

		} catch (
				RuntimeException e
		) {

			log.warn(
					"[AI-MEMORY] payload JSON 파싱 실패.",
					e
			);


			return jsonMapper
					.createObjectNode();
		}
	}


	/**
	 * JSON 문자열 필드 안전 조회.
	 */
	private String readText(
			JsonNode payload,
			String field
	) {

		JsonNode node =
				payload.get(
						field
				);


		if (
				node == null
						||
						node.isNull()
		) {

			return null;
		}


		try {

			String value =
					node.asString();


			if (
					value == null
							||
							value.isBlank()
			) {

				return null;
			}


			return value.trim();

		} catch (
				RuntimeException ignored
		) {

			return null;
		}
	}


	/**
	 * FACT_KEY에 들어갈 값을 안전하게 정규화한다.
	 *
	 * 예:
	 *
	 * ALONE
	 * → alone
	 *
	 * FAMILY TRIP
	 * → family_trip
	 */
	private String normalizeKeyComponent(
			String value
	) {

		if (
				value == null
						||
						value.isBlank()
		) {

			return null;
		}


		String normalized =
				value
						.trim()
						.toLowerCase(
								Locale.ROOT
						)
						.replaceAll(
								"[^a-z0-9가-힣_-]+",
								"_"
						)
						.replaceAll(
								"^_+|_+$",
								""
						);


		if (
				normalized.isBlank()
		) {

			return null;
		}


		/*
		 * FACT_KEY 전체 길이가 120이므로
		 * 값 부분은 여유 있게 최대 50자로 제한한다.
		 */
		if (
				normalized.length() > 50
		) {

			normalized =
					normalized.substring(
							0,
							50
					);
		}


		return normalized;
	}


	/**
	 * JSON 직렬화 Helper.
	 */
	private String toJson(
			Object value
	) {

		try {

			return jsonMapper
					.writeValueAsString(
							value
					);

		} catch (
				RuntimeException e
		) {

			throw new IllegalStateException(
					"AI User Memory Fact JSON 직렬화에 실패했습니다.",
					e
			);
		}
	}
}