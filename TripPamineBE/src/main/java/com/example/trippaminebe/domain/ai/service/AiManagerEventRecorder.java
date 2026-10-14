package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.entity.AiManagerEvent;
import com.example.trippaminebe.domain.ai.event.AiManagerEventRecordedApplicationEvent;
import com.example.trippaminebe.domain.ai.repository.AiManagerEventRepository;
import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import com.example.trippaminebe.domain.quest.event.QuestCompletedApplicationEvent;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.travel.event.TravelPlanActivityApplicationEvent;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * AI_MANAGER_EVENTS 기록 전용 Service.
 *
 * =========================================================
 * 이 클래스의 역할
 * =========================================================
 *
 * 여러 Domain에서 발생한 사건을
 * AI_MANAGER_EVENTS라는 하나의 Event Log로 저장한다.
 *
 * 현재 연결된 Domain:
 *
 * 1. Emotion
 *    - 기존 감정 저장 로직에서 처리
 *
 * 2. Quest
 *    - QUEST_COMPLETED
 *
 * 3. Travel
 *    - TRAVEL_PLAN_CREATED
 *    - TRAVEL_PLAN_UPDATED
 *    - TRAVEL_PLAN_DELETED
 *
 *
 * 중요한 설계 원칙:
 *
 * QuestService나 TravelPlanService가
 * AI_MANAGER_EVENTS Repository를 직접 사용하지 않는다.
 *
 * 대신:
 *
 * Domain Service
 *      ↓
 * Application Event
 *      ↓
 * AI Manager Listener
 *      ↓
 * 이 Recorder
 *      ↓
 * AI_MANAGER_EVENTS
 *
 * 구조로 Domain 간 결합도를 낮춘다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerEventRecorder {

	private final AiManagerEventRepository
			aiManagerEventRepository;

	private final UserRepository
			userRepository;

	private final TravelPlanRepository
			travelPlanRepository;

	private final JsonMapper
			jsonMapper;

	private final AiTimeProvider
			aiTimeProvider;

	private final ApplicationEventPublisher
			applicationEventPublisher;


	/* =====================================================
	 * QUEST EVENT
	 * ===================================================== */

	/**
	 * Quest 완료 Event를 AI_MANAGER_EVENTS에 저장한다.
	 *
	 * Listener의 AFTER_COMMIT 이후 호출되므로
	 * 별도 Transaction을 사용한다.
	 *
	 * @return 새 AI_MANAGER_EVENTS.EVENT_ID
	 *         이미 같은 Event가 존재하면 null
	 */
	@Transactional(
			propagation = Propagation.REQUIRES_NEW
	)
	public Long recordQuestCompleted(
			QuestCompletedApplicationEvent event
	) {

		/* -------------------------------------------------
		 * 1. 중복 방지 Key
		 * -------------------------------------------------
		 *
		 * Quest Log 하나는 한 번만 성공할 수 있으므로:
		 *
		 * QUEST_COMPLETED:37
		 *
		 * 형태로 고정 Key를 만든다.
		 */
		String dedupeKey =
				"QUEST_COMPLETED:"
						+ event.logId();


		if (
				aiManagerEventRepository
						.existsByDedupeKey(
								dedupeKey
						)
		) {

			return null;
		}


		/* -------------------------------------------------
		 * 2. User FK
		 * -------------------------------------------------
		 *
		 * getReferenceById()는 User 전체 Row를
		 * 즉시 SELECT하지 않고 FK 연결용 Proxy를 얻는다.
		 */
		User user =
				userRepository
						.getReferenceById(
								event.userId()
						);


		/* -------------------------------------------------
		 * 3. Payload
		 * ------------------------------------------------- */

		Map<String, Object> payload =
				new LinkedHashMap<>();


		payload.put(
				"logId",
				event.logId()
		);

		payload.put(
				"questId",
				event.questId()
		);

		payload.put(
				"questName",
				event.questName()
		);

		payload.put(
				"rewardPoint",
				event.rewardPoint()
		);

		payload.put(
				"clearDate",
				event.clearDate() != null
						? event.clearDate().toString()
						: null
		);


		/* -------------------------------------------------
		 * 4. 실제 발생 시각
		 * -------------------------------------------------
		 *
		 * Quest 완료 Event의 실제 발생 시각은
		 * USER_QUEST_LOGS.CLEAR_DATE다.
		 *
		 * 이것을 AI_MANAGER_EVENTS.OCCURRED_AT으로 사용한다.
		 */
		LocalDateTime occurredAt =
				event.clearDate() != null
						? event.clearDate()
						: aiTimeProvider.now();


		/* -------------------------------------------------
		 * 5. Event Entity 생성
		 * ------------------------------------------------- */

		AiManagerEvent managerEvent =
				AiManagerEvent
						.builder()

						.user(
								user
						)

						.eventType(
								"QUEST_COMPLETED"
						)

						.sourceType(
								"QUEST"
						)

						/*
						 * 원본 Domain 데이터:
						 * USER_QUEST_LOGS.LOG_ID
						 */
						.sourceId(
								String.valueOf(
										event.logId()
								)
						)

						.dedupeKey(
								dedupeKey
						)

						.payloadJson(
								toJson(
										payload
								)
						)

						/*
						 * Oracle NOT NULL 컬럼.
						 *
						 * 이전 ORA-01400 문제 때문에
						 * 반드시 명시적으로 지정한다.
						 */
						.occurredAt(
								occurredAt
						)

						.build();


		/*
		 * 검증 단계에서는 saveAndFlush()를 사용해
		 * Oracle INSERT 오류를 여기서 바로 감지한다.
		 */
		AiManagerEvent savedEvent =
				aiManagerEventRepository
						.saveAndFlush(
								managerEvent
						);


		/*
		 * AI_MANAGER_EVENTS 저장 Transaction이
		 * 실제 COMMIT된 뒤 Memory Pipeline이 실행되도록
		 * 공통 Application Event를 발행한다.
		 */
		publishRecordedEvent(
				savedEvent
		);


		return savedEvent.getEventId();
	}


	/* =====================================================
	 * TRAVEL EVENT
	 * ===================================================== */

	/**
	 * 여행 계획의 생성/수정/삭제 Event를 저장한다.
	 *
	 * 이 메서드가 현재 IntelliJ에서 찾지 못하고 있는
	 * recordTravelPlanActivity() 메서드다.
	 *
	 * @return 새 AI_MANAGER_EVENTS.EVENT_ID
	 *         중복 Event라면 null
	 */
	@Transactional(
			propagation = Propagation.REQUIRES_NEW
	)
	public Long recordTravelPlanActivity(
			TravelPlanActivityApplicationEvent event
	) {

		/* -------------------------------------------------
		 * 1. Application Event Type
		 *    → DB EVENT_TYPE 변환
		 * ------------------------------------------------- */

		String eventType =
				switch (event.type()) {

					case CREATED ->
							"TRAVEL_PLAN_CREATED";

					case UPDATED ->
							"TRAVEL_PLAN_UPDATED";

					case DELETED ->
							"TRAVEL_PLAN_DELETED";
				};


		/* -------------------------------------------------
		 * 2. Dedupe Key 생성
		 * -------------------------------------------------
		 *
		 * CREATED:
		 * 여행 하나당 한 번만 생성
		 *
		 * DELETED:
		 * 여행 하나당 한 번만 삭제
		 *
		 * UPDATED:
		 * 같은 여행도 여러 번 수정 가능
		 *
		 * 따라서 UPDATE에는 발생 시각을 포함한다.
		 */
		String dedupeKey =
				switch (event.type()) {

					case CREATED,
					     DELETED ->

							eventType
									+ ":"
									+ event.planId();


					case UPDATED ->

							eventType
									+ ":"
									+ event.planId()
									+ ":"
									+ event.occurredAt();
				};


		/*
		 * Application 수준 중복 방지.
		 */
		if (
				aiManagerEventRepository
						.existsByDedupeKey(
								dedupeKey
						)
		) {

			return null;
		}


		/* -------------------------------------------------
		 * 3. User FK
		 * ------------------------------------------------- */

		User user =
				userRepository
						.getReferenceById(
								event.userId()
						);


		/* -------------------------------------------------
		 * 4. TravelPlan FK
		 * -------------------------------------------------
		 *
		 * TravelPlan 삭제는 실제 Row DELETE가 아니라
		 *
		 * DEL_YN = 'Y'
		 *
		 * 방식의 Soft Delete다.
		 *
		 * 따라서 DELETE Event에서도 PLAN_ID FK를
		 * 계속 연결할 수 있다.
		 */
		TravelPlan travelPlan =
				travelPlanRepository
						.getReferenceById(
								event.planId()
						);


		/* -------------------------------------------------
		 * 5. Payload
		 * -------------------------------------------------
		 *
		 * Event 발생 당시의 여행 Snapshot을 남긴다.
		 *
		 * 나중에 여행이 다시 변경되어도
		 * "그 당시 어떤 여행이었는지"를 알 수 있다.
		 */
		Map<String, Object> payload =
				new LinkedHashMap<>();


		payload.put(
				"planId",
				event.planId()
		);

		payload.put(
				"planName",
				event.planName()
		);

		payload.put(
				"totalBudget",
				event.totalBudget()
		);

		payload.put(
				"companionType",
				event.companionType()
		);

		payload.put(
				"locationCd",
				event.locationCd()
		);

		payload.put(
				"blindYn",
				event.blindYn()
		);

		payload.put(
				"startDate",
				event.startDate() != null
						? event.startDate().toString()
						: null
		);

		payload.put(
				"endDate",
				event.endDate() != null
						? event.endDate().toString()
						: null
		);


		/* -------------------------------------------------
		 * 6. occurredAt
		 * -------------------------------------------------
		 *
		 * Application Event가 전달한 실제 발생 시각을 사용한다.
		 *
		 * 혹시 null이 들어오더라도
		 * Oracle NOT NULL 오류가 발생하지 않도록 fallback한다.
		 */
		LocalDateTime occurredAt =
				event.occurredAt() != null
						? event.occurredAt()
						: aiTimeProvider.now();


		/* -------------------------------------------------
		 * 7. Event Entity 생성
		 * ------------------------------------------------- */

		AiManagerEvent managerEvent =
				AiManagerEvent
						.builder()

						.user(
								user
						)

						/*
						 * AI_MANAGER_EVENTS.PLAN_ID 연결.
						 */
						.travelPlan(
								travelPlan
						)

						.eventType(
								eventType
						)

						.sourceType(
								"TRAVEL"
						)

						/*
						 * 원본 Domain 식별자는
						 * TRAVEL_PLANS.PLAN_ID.
						 */
						.sourceId(
								String.valueOf(
										event.planId()
								)
						)

						.dedupeKey(
								dedupeKey
						)

						.payloadJson(
								toJson(
										payload
								)
						)

						.occurredAt(
								occurredAt
						)

						.build();


		/* -------------------------------------------------
		 * 8. 실제 INSERT
		 * ------------------------------------------------- */

		AiManagerEvent savedEvent =
				aiManagerEventRepository
						.saveAndFlush(
								managerEvent
						);


		publishRecordedEvent(
				savedEvent
		);


		return savedEvent.getEventId();
	}


	/* =====================================================
	 * JSON Helper
	 * ===================================================== */

	/**
	 * AI_MANAGER_EVENTS 저장 완료 사실을
	 * AI Manager 내부 Pipeline에 알린다.
	 *
	 *
	 * publishEvent() 자체는 현재 Transaction 안에서 호출되지만,
	 *
	 * AiUserMemoryEvidenceListener가
	 *
	 * TransactionPhase.AFTER_COMMIT
	 *
	 * 으로 동작하므로 실제 Memory 처리는
	 * AI_MANAGER_EVENTS INSERT가 COMMIT된 후 시작된다.
	 */
	private void publishRecordedEvent(
			AiManagerEvent savedEvent
	) {

		if (
				savedEvent == null
						||
						savedEvent.getEventId() == null
		) {

			return;
		}


		applicationEventPublisher
				.publishEvent(

						new AiManagerEventRecordedApplicationEvent(

								savedEvent.getEventId(),

								savedEvent
										.getUser()
										.getId(),

								savedEvent.getEventType(),

								savedEvent.getOccurredAt()
						)
				);
	}

	/**
	 * Java Object를 JSON 문자열로 변환한다.
	 *
	 * JSON Mapper 내부 예외를
	 * AI Manager Domain 의미가 명확한 예외로 감싼다.
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
					"AI Manager Event payload JSON 직렬화에 실패했습니다.",
					e
			);
		}
	}
}