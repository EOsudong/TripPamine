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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
		 * =========================================================
		 * Travel Activity 전용 Mapping
		 * =========================================================
		 *
		 * 이전에는 SOURCE_TYPE=TRAVEL이라는 정보만 보고
		 * 모든 Travel Event를 같은 문구로 표시했다.
		 *
		 * 이제 EVENT_TYPE까지 구분해:
		 *
		 * TRAVEL_PLAN_CREATED
		 * TRAVEL_PLAN_UPDATED
		 * TRAVEL_PLAN_DELETED
		 *
		 * 각각 다른 사용자용 문구로 변환한다.
		 */
		if (
				event.getEventType() != null
						&&
						event.getEventType().startsWith(
								"TRAVEL_PLAN_"
						)
		) {

			return toTravelActivity(
					event
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

	/**
	 * =========================================================
	 * Travel Event → Activity
	 * =========================================================
	 *
	 * AI_MANAGER_EVENTS.PAYLOAD_JSON에 저장해 둔
	 * 여행 당시 Snapshot을 이용해 사용자에게 보여줄 문구를 만든다.
	 *
	 * 중요한 이유:
	 *
	 * TRAVEL_PLAN_DELETED Event의 경우
	 * 현재 여행 목록에서는 이미 DEL_YN='Y'로 제외된다.
	 *
	 * 따라서 현재 TRAVEL_PLANS를 다시 조회해서 제목을 만들기보다,
	 * Event 발생 당시 저장해 둔 PAYLOAD_JSON을 사용하는 것이
	 * Event Log 구조에 더 적합하다.
	 */
	private AiManagerHomeResponse.ActivityEvent toTravelActivity(
			AiManagerEvent event
	) {

		JsonNode payload =
				readTravelPayload(
						event
				);


		/*
		 * Event 생성 당시 저장한 여행 이름.
		 */
		String planName =
				travelPayloadText(
						payload,
						"planName",
						"여행 계획"
				);


		/*
		 * CREATE / UPDATE에서 사용자에게
		 * 출발일을 함께 보여주기 위한 문자열.
		 */
		String startDateText =
				formatTravelStartDate(
						payload
				);


		String title;

		String description;


		switch (
				event.getEventType()
		) {

			/* =================================================
			 * CREATE
			 * ================================================= */

			case "TRAVEL_PLAN_CREATED" -> {

				title =
						"새 여행 계획이 등록됐어요";


				description =
						buildTravelPlanDescription(
								planName,
								startDateText
						);
			}


			/* =================================================
			 * UPDATE
			 * ================================================= */

			case "TRAVEL_PLAN_UPDATED" -> {

				title =
						"여행 계획이 변경됐어요";


				description =
						buildTravelPlanDescription(
								planName,
								startDateText
						);
			}


			/* =================================================
			 * DELETE
			 * ================================================= */

			case "TRAVEL_PLAN_DELETED" -> {

				title =
						"여행 계획이 삭제됐어요";


				description =
						planName
								+ " 여행 계획이 삭제되었습니다.";
			}


			/*
			 * 현재는 위 3종만 존재하지만
			 * 앞으로 TRAVEL_PLAN_STARTED 등의 Event가 추가될 수 있다.
			 *
			 * 알 수 없는 Travel Event라고 해서
			 * Activity 전체 조회가 실패하면 안 되므로 fallback한다.
			 */
			default -> {

				title =
						"여행 계획에 변화가 있어요";


				description =
						planName;
			}
		}


		return new AiManagerHomeResponse.ActivityEvent(

				event.getEventId(),

				"travel",

				event.getEventType(),

				event.getSourceType(),

				event.getSourceId(),

				title,

				description,

				resolveOccurredAt(
						event
				)
		);
	}


	/**
	 * Travel Payload의 문자열 값을 안전하게 읽는다.
	 */
	private String travelPayloadText(
			JsonNode payload,
			String fieldName,
			String fallback
	) {

		if (
				payload == null
						||
						fieldName == null
						||
						fieldName.isBlank()
		) {

			return fallback;
		}


		JsonNode valueNode =
				payload.get(
						fieldName
				);


		if (
				valueNode == null
						||
						valueNode.isNull()
		) {

			return fallback;
		}


		/*
		 * Jackson 3 JsonNode의 문자열 값을 읽는다.
		 */
		String value =
				valueNode.asString();


		if (
				value == null
						||
						value.isBlank()
		) {

			return fallback;
		}


		return value;
	}


	/**
	 * Event Payload의 startDate를
	 * 사용자 화면용 "M월 d일 출발" 형식으로 변환한다.
	 *
	 * DB 저장 형식 예:
	 *
	 * 2026-10-11T02:46
	 *
	 * 화면:
	 *
	 * 10월 11일 출발
	 */
	private String formatTravelStartDate(
			JsonNode payload
	) {

		String startDate =
				travelPayloadText(
						payload,
						"startDate",
						null
				);


		if (
				startDate == null
						||
						startDate.isBlank()
		) {

			return null;
		}


		try {

			LocalDateTime dateTime =
					LocalDateTime.parse(
							startDate
					);


			DateTimeFormatter formatter =
					DateTimeFormatter.ofPattern(
							"M월 d일"
					);


			return dateTime
					.format(
							formatter
					)
					+ " 출발";

		} catch (
				DateTimeParseException e
		) {

			/*
			 * Event Payload 하나의 날짜 문자열이 잘못됐다고 해서
			 * AI Manager Home 전체가 500이 되면 안 된다.
			 *
			 * 날짜만 표시하지 않고 나머지 Activity는 정상 반환한다.
			 */
			return null;
		}
	}


	/**
	 * CREATE / UPDATE 설명 공통 조립.
	 */
	private String buildTravelPlanDescription(
			String planName,
			String startDateText
	) {

		if (
				startDateText == null
						||
						startDateText.isBlank()
		) {

			return planName;
		}


		return planName
				+ " · "
				+ startDateText;
	}


	/**
	 * 실제 Event 발생시각을 반환한다.
	 *
	 * OCCURRED_AT을 우선하고,
	 * 예전 Event처럼 값이 없는 경우 CREATED_AT으로 fallback한다.
	 */
	private LocalDateTime resolveOccurredAt(
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
	 * Travel Event의 PAYLOAD_JSON을 직접 읽는다.
	 *
	 * 기존 readPayload()가 Emotion/Quest 중심으로 작성되어 있거나,
	 * Event별 Payload 처리 방식이 달라 Travel Payload를 제대로 읽지 못하는
	 * 상황을 피하기 위해 Travel 전용 Parser를 둔다.
	 *
	 * AI_MANAGER_EVENTS.PAYLOAD_JSON 예:
	 *
	 * {
	 *   "planId": 58,
	 *   "planName": "AI Manager Event Update Test",
	 *   "totalBudget": 150000,
	 *   "companionType": "ALONE",
	 *   "locationCd": "ETC",
	 *   "blindYn": "N",
	 *   "startDate": "2026-10-11T02:46",
	 *   "endDate": "2026-10-13T02:46"
	 * }
	 */
	private JsonNode readTravelPayload(
			AiManagerEvent event
	) {

		if (
				event == null
						||
						event.getPayloadJson() == null
						||
						event.getPayloadJson().isBlank()
		) {

			return null;
		}


		try {

			/*
			 * Spring Boot 4 / Jackson 3.
			 *
			 * PAYLOAD_JSON 문자열 자체를 JsonNode Tree로 변환한다.
			 */
			return jsonMapper.readTree(
					event.getPayloadJson()
			);

		} catch (
				Exception e
		) {

			/*
			 * Activity 하나의 JSON이 깨져 있다고 해서
			 * /api/ai-manager/home 전체가 500이 되면 안 된다.
			 *
			 * null을 반환하면 이후 Mapper가 fallback 문구를 사용한다.
			 */
			return null;
		}
	}
}