package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "AI_EMOTION_OBSERVATIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiEmotionObservation {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_EMOTION_OBSERVATIONS_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_EMOTION_OBSERVATIONS_GEN",
			sequenceName = "SEQ_AI_EMOTION_OBSERVATIONS",
			allocationSize = 1
	)
	@Column(name = "OBSERVATION_ID")
	private Long observationId;

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
	@JoinColumn(name = "EVENT_ID")
	private AiManagerEvent event;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "MODEL_RUN_ID",
			nullable = false
	)
	private AiModelRun modelRun;

	@Column(
			name = "FINE_EMOTION",
			nullable = false,
			length = 10
	)
	private String fineEmotion;

	@Column(
			name = "FINE_EMOTION_NAME",
			nullable = false,
			length = 100
	)
	private String fineEmotionName;

	@Column(
			name = "COARSE_EMOTION",
			nullable = false,
			length = 30
	)
	private String coarseEmotion;

	@Column(
			name = "CONFIDENCE",
			nullable = false,
			precision = 10,
			scale = 8
	)
	private BigDecimal confidence;

	@Lob
	@Column(name = "TOP_K_JSON")
	private String topKJson;

	/**
	 * 감정이 실제로 관측된 시각.
	 *
	 * Oracle DB의 SYSTIMESTAMP에 의존하지 않고
	 * Application Service에서 명시적으로 값을 넣는다.
	 */
	@Column(
			name = "OBSERVED_AT",
			nullable = false,
			updatable = false
	)
	private LocalDateTime observedAt;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;
}