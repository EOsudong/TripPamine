package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


/**
 * AI Manager가 관찰한 사용자 사건(Event)을 저장하는 Entity.
 *
 * =========================================================
 * AI_MANAGER_EVENTS 역할
 * =========================================================
 *
 * 이 테이블은 "현재 상태"를 저장하는 Snapshot Table이 아니라
 * "무슨 일이 언제 발생했는가"를 누적하는 Event Log이다.
 *
 * 예:
 *
 * - EMOTION_ANALYZED
 * - QUEST_COMPLETED
 * - TRAVEL_STARTED
 * - DIARY_CREATED
 * - FINANCE_CONTEXT_UPDATED
 *
 *
 * Event를 누적해두면 나중에:
 *
 * 1. AI Manager Activity Feed
 * 2. User Memory Evidence
 * 3. Insight 생성 근거
 * 4. 행동 패턴 분석
 *
 * 등에 재사용할 수 있다.
 */
@Entity
@Table(name = "AI_MANAGER_EVENTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiManagerEvent {

	/* =====================================================
	 * Primary Key
	 * ===================================================== */

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_MANAGER_EVENTS_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_MANAGER_EVENTS_GEN",
			sequenceName = "SEQ_AI_MANAGER_EVENTS",
			allocationSize = 1
	)
	@Column(name = "EVENT_ID")
	private Long eventId;


	/* =====================================================
	 * Owner
	 * ===================================================== */

	/**
	 * 이 Event의 사용자.
	 *
	 * 모든 AI Manager Event는 특정 사용자에게 속하므로
	 * NOT NULL이다.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "USER_ID",
			nullable = false
	)
	private User user;


	/**
	 * 여행과 직접 관련된 Event일 경우 연결한다.
	 *
	 * 감정 전체 분석이나 Quest처럼
	 * 특정 여행과 무관한 Event는 null일 수 있다.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "PLAN_ID")
	private TravelPlan travelPlan;


	/**
	 * AI Model 실행 결과에서 만들어진 Event라면
	 * 해당 AI_MODEL_RUNS Row와 연결한다.
	 *
	 * Quest 완료처럼 Model 실행과 무관한 Event는 null이다.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "MODEL_RUN_ID")
	private AiModelRun modelRun;


	/* =====================================================
	 * Event Identity
	 * ===================================================== */

	/**
	 * "무슨 일이 일어났는가?"
	 *
	 * 예:
	 *
	 * EMOTION_ANALYZED
	 * QUEST_COMPLETED
	 */
	@Column(
			name = "EVENT_TYPE",
			nullable = false,
			length = 60
	)
	private String eventType;


	/**
	 * "어느 Domain / 시스템에서 발생했는가?"
	 *
	 * 예:
	 *
	 * EMOTION_API
	 * QUEST
	 * TRAVEL
	 */
	@Column(
			name = "SOURCE_TYPE",
			nullable = false,
			length = 50
	)
	private String sourceType;


	/**
	 * 원본 Domain 데이터의 식별자.
	 *
	 * 예:
	 *
	 * Quest:
	 * USER_QUEST_LOGS.LOG_ID
	 *
	 * Emotion:
	 * requestId
	 */
	@Column(
			name = "SOURCE_ID",
			length = 150
	)
	private String sourceId;


	/**
	 * 동일 Event의 중복 저장을 막기 위한 논리 Key.
	 *
	 * 예:
	 *
	 * QUEST_COMPLETED:38
	 */
	@Column(
			name = "DEDUPE_KEY",
			length = 200
	)
	private String dedupeKey;


	/* =====================================================
	 * Payload
	 * ===================================================== */

	/**
	 * Domain별 부가 정보.
	 *
	 * 모든 Event마다 별도의 테이블 컬럼을 추가하지 않고
	 * Event 고유 데이터는 JSON으로 보관한다.
	 *
	 * 예:
	 *
	 * {
	 *   "questId": 15,
	 *   "questName": "...",
	 *   "rewardPoint": 500
	 * }
	 */
	@Lob
	@Column(name = "PAYLOAD_JSON")
	private String payloadJson;


	/* =====================================================
	 * Time
	 * ===================================================== */

	/**
	 * 사건이 실제 발생한 시간.
	 *
	 * 매우 중요:
	 *
	 * CREATED_AT과 의미가 다르다.
	 *
	 * OCCURRED_AT
	 * = 실제 사건 발생 시간
	 *
	 * CREATED_AT
	 * = DB Row가 기록된 시간
	 *
	 *
	 * Quest의 경우:
	 *
	 * clearDate
	 * → OCCURRED_AT
	 *
	 * 으로 저장한다.
	 *
	 * AI_MANAGER_EVENTS.OCCURRED_AT은 실제 DB에서
	 * NOT NULL이므로 JPA에서도 nullable=false로 맞춘다.
	 */
	@Column(
			name = "OCCURRED_AT",
			nullable = false,
			updatable = false
	)
	private LocalDateTime occurredAt;


	/**
	 * DB Row 생성 시각.
	 *
	 * 현재 Oracle Schema의 DEFAULT 값을 그대로 사용한다.
	 *
	 * 따라서 INSERT 시 Hibernate가 값을 보내지 않고
	 * Oracle이 생성하도록 insertable=false로 둔다.
	 */
	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;


	/* =====================================================
	 * Lifecycle
	 * ===================================================== */

	/**
	 * Event를 생성하는 코드에서 OCCURRED_AT을 빠뜨려도
	 * NOT NULL 오류로 전체 Event 저장이 실패하지 않도록
	 * 마지막 안전장치를 둔다.
	 *
	 * 주의:
	 *
	 * 이것은 fallback이다.
	 *
	 * 실제 Domain에서 사건 발생시각을 알고 있다면
	 * Builder에서 occurredAt을 직접 지정하는 것이 우선이다.
	 */
	@PrePersist
	protected void prePersist() {

		if (this.occurredAt == null) {
			this.occurredAt =
					LocalDateTime.now();
		}
	}
}