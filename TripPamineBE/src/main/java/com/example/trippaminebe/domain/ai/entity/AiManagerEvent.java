package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "AI_MANAGER_EVENTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiManagerEvent {

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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "USER_ID",
			nullable = false
	)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "PLAN_ID")
	private TravelPlan travelPlan;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "MODEL_RUN_ID")
	private AiModelRun modelRun;

	@Column(
			name = "EVENT_TYPE",
			nullable = false,
			length = 60
	)
	private String eventType;

	@Column(
			name = "SOURCE_TYPE",
			nullable = false,
			length = 50
	)
	private String sourceType;

	@Column(
			name = "SOURCE_ID",
			length = 150
	)
	private String sourceId;

	@Column(
			name = "DEDUPE_KEY",
			length = 200
	)
	private String dedupeKey;

	@Lob
	@Column(name = "PAYLOAD_JSON")
	private String payloadJson;

	/**
	 * AI Manager 이벤트가 실제 발생한 서비스 기준 시각.
	 */
	@Column(
			name = "OCCURRED_AT",
			nullable = false,
			updatable = false
	)
	private LocalDateTime occurredAt;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;
}