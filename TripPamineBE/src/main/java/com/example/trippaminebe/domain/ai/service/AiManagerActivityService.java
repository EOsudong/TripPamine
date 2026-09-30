package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.entity.AiEmotionObservation;
import com.example.trippaminebe.domain.ai.entity.AiManagerEvent;
import com.example.trippaminebe.domain.ai.repository.AiEmotionObservationRepository;
import com.example.trippaminebe.domain.ai.repository.AiManagerEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * AI_MANAGER_EVENTS를
 * AI Manager Home Activity Feed용 DTO로 변환한다.
 *
 *
 * =========================================================
 * 책임
 * =========================================================
 *
 * DB:
 *
 * AI_MANAGER_EVENTS
 *
 *      ↓
 *
 * Domain Mapping:
 *
 * Emotion / Quest / Travel / Finance ...
 *
 *      ↓
 *
 * AiManagerHomeResponse.ActivityEvent
 *
 *      ↓
 *
 * Frontend Activity Feed
 *
 *
 * Entity 자체를 Controller에서 바로 반환하지 않는 이유:
 *
 * 1. JPA 내부 구조 노출 방지
 * 2. payloadJson 내부 Schema 노출 방지
 * 3. Frontend와 DB Schema 직접 결합 방지
 * 4. 향후 Event Schema 변경 용이
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerActivityService {

	private final AiManagerEventRepository
			aiManagerEventRepository;

	private final AiEmotionObservationRepository
			aiEmotionObservationRepository;


	/**
	 * Spring Boot 4 + Jackson 3 기본 JSON Mapper.
	 */
	private final JsonMapper
			jsonMapper;


	/**
	 * 현재 사용자의 최근 Activity를 반환한다.
	 *
	 * Repository:
	 * 최대 20개
	 *
	 * Home UI:
	 * 최대 10개
	 *
	 * 로 제한한다.
	 */
	public List<AiManagerHomeResponse.ActivityEvent>
	findRecent(
			Long userId
	) {

		/*
		 * =================================================
		 * 1. 최근 Event 조회
		 * =================================================
		 */
		List<AiManagerEvent> events =
				aiManagerEventRepository
						.findTop20ByUser_IdOrderByCreatedAtDesc(
								userId
						);


		if (
				events.isEmpty()
		) {

			return List.of();
		}


		/*
		 * =================================================
		 * 2. Event ID 수집
		 * =================================================
		 *
		 * 감정 Observation을 Event마다 하나씩 조회하지 않고
		 * IN Query 한 번으로 조회하기 위한 준비 단계다.
		 */
		List<Long> eventIds =
				events.stream()

						.map(
								AiManagerEvent::getEventId
						)

						.toList();


		/*
		 * =================================================
		 * 3. Emotion Observation 일괄 조회
		 * =================================================
		 */
		List<AiEmotionObservation> observations =
				eventIds.isEmpty()

						? Collections.emptyList()

						: aiEmotionObservationRepository
						.findByEvent_EventIdIn(
								eventIds
						);


		/*
		 * =================================================
		 * 4. EVENT_ID → Emotion Observation Map
		 * =================================================
		 *
		 * Event Timeline을 순회하면서 감정 데이터를
		 * 빠르게 찾기 위해 Map으로 변경한다.
		 *
		 * Event 하나를 찾을 때마다 List 전체를 검색하면:
		 *
		 * O(N²)
		 *
		 * Map으로 만들면:
		 *
		 * O(1)
		 *
		 * 수준으로 조회할 수 있다.
		 */
		Map<Long, AiEmotionObservation>
				emotionByEventId =

				observations
						.stream()

						.collect(
								Collectors.toMap(

										observation ->
												observation
														.getEvent()
														.getEventId(),

										Function.identity(),

										/*
										 * 정상 데이터에서는
										 * Event 1개에 Observation 1개다.
										 *
										 * 중복 데이터가 존재하더라도
										 * 첫 번째 값을 유지한다.
										 */
										(
												first,
												ignored
										) ->
												first
								)
						);


		/*
		 * =================================================
		 * 5. Event → 화면 DTO
		 * =================================================
		 */
		return events
				.stream()

				.map(
						event ->
								toActivity(

										event,

										emotionByEventId
												.get(
														event.getEventId()
												)
								)
				)

				/*
				 * Home에서는 최근 10개까지만 노출.
				 */
				.limit(
						10
				)

				.toList();
	}


	/**
	 * DB Event 한 건을
	 * 화면에서 사용하는 Activity DTO로 변환한다.
	 */
	private AiManagerHomeResponse.ActivityEvent
	toActivity(
			AiManagerEvent event,
			AiEmotionObservation emotion
	) {

		/*
		 * =================================================
		 * Emotion Event
		 * =================================================
		 *
		 * Event와 연결된 Emotion Observation이 있다면
		 * EVENT_TYPE 문자열 추측보다
		 * 실제 FK Relation을 더 신뢰한다.
		 */
		if (
				emotion != null
		) {

			return new AiManagerHomeResponse.ActivityEvent(

					event.getEventId(),

					"emotion",

					event.getEventType(),

					event.getSourceType(),

					event.getSourceId(),

					"최근 감정이 기록됐어요",

					"'"
							+ emotion.getFineEmotionName()
							+ "' 상태로 관찰되었습니다.",

					effectiveOccurredAt(
							event
					)
			);
		}


		/*
		 * =================================================
		 * Quest 완료 Event
		 * =================================================
		 */
		if (
				"QUEST_COMPLETED"
						.equalsIgnoreCase(
								event.getEventType()
						)
		) {

			JsonNode payload =
					readPayload(
							event.getPayloadJson()
					);


			String questName =
					readText(

							payload,

							"questName",

							"퀘스트"
					);


			long rewardPoint =
					readLong(

							payload,

							"rewardPoint",

							0L
					);


			return new AiManagerHomeResponse.ActivityEvent(

					event.getEventId(),

					"quest",

					event.getEventType(),

					event.getSourceType(),

					event.getSourceId(),

					"퀘스트를 완료했어요",

					questName
							+ " · +"
							+ rewardPoint
							+ " P",

					effectiveOccurredAt(
							event
					)
			);
		}


		/*
		 * =================================================
		 * Generic Event
		 * =================================================
		 *
		 * 아직 전용 Mapper가 구현되지 않은 Event도
		 * Timeline에서 완전히 사라지게 하지 않는다.
		 */
		String domain =
				normalizeDomain(
						event.getSourceType()
				);


		return new AiManagerHomeResponse.ActivityEvent(

				event.getEventId(),

				domain,

				event.getEventType(),

				event.getSourceType(),

				event.getSourceId(),

				defaultTitle(
						domain
				),

				"새로운 활동이 기록되었습니다.",

				effectiveOccurredAt(
						event
				)
		);
	}


	/**
	 * 실제 사건 발생 시각 우선.
	 *
	 * OCCURRED_AT이 null인 과거 데이터는
	 * CREATED_AT으로 fallback한다.
	 */
	private LocalDateTime effectiveOccurredAt(
			AiManagerEvent event
	) {

		if (
				event.getOccurredAt() != null
		) {

			return event.getOccurredAt();
		}


		return event.getCreatedAt();
	}


	/**
	 * SOURCE_TYPE을
	 * Frontend Domain 이름으로 정규화한다.
	 */
	private String normalizeDomain(
			String sourceType
	) {

		if (
				sourceType == null
						||
						sourceType.isBlank()
		) {

			return "system";
		}


		return switch (
				sourceType.toUpperCase()
				) {

			case "TRAVEL" ->
					"travel";

			case "QUEST" ->
					"quest";

			case "EMOTION" ->
					"emotion";

			case "FINANCE" ->
					"finance";

			case "DIARY" ->
					"diary";

			default ->
					"system";
		};
	}


	/**
	 * 아직 Event별 전용 화면 문구가 없을 때
	 * Domain 기준 기본 제목을 만든다.
	 */
	private String defaultTitle(
			String domain
	) {

		return switch (
				domain
				) {

			case "travel" ->
					"여행 계획이 업데이트됐어요";

			case "quest" ->
					"퀘스트 활동이 기록됐어요";

			case "emotion" ->
					"감정 상태가 업데이트됐어요";

			case "finance" ->
					"자산 정보가 업데이트됐어요";

			case "diary" ->
					"새로운 기록이 추가됐어요";

			default ->
					"AI Manager 활동이 기록됐어요";
		};
	}


	/**
	 * PAYLOAD_JSON을 JsonNode로 변환한다.
	 *
	 * 잘못된 JSON 한 건 때문에
	 * 전체 Home API가 500이 되는 것은 좋지 않다.
	 *
	 * 따라서 실패 시 빈 JSON Object를 반환한다.
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
				RuntimeException ignored
		) {

			return jsonMapper
					.createObjectNode();
		}
	}


	/**
	 * JSON String 안전 조회.
	 *
	 * Jackson 3에서는 asString()을 사용할 수 있다.
	 */
	private String readText(
			JsonNode payload,
			String field,
			String fallback
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

			return fallback;
		}


		try {

			String value =
					node.asString();


			if (
					value == null
							||
							value.isBlank()
			) {

				return fallback;
			}


			return value;

		} catch (
				RuntimeException ignored
		) {

			return fallback;
		}
	}


	/**
	 * JSON Long 안전 조회.
	 */
	private long readLong(
			JsonNode payload,
			String field,
			long fallback
	) {

		JsonNode node =
				payload.get(
						field
				);


		if (
				node == null
						||
						node.isNull()
						||
						!node.isNumber()
		) {

			return fallback;
		}


		try {

			return node.asLong();

		} catch (
				RuntimeException ignored
		) {

			return fallback;
		}
	}
}