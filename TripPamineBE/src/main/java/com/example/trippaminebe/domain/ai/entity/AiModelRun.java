package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "AI_MODEL_RUNS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiModelRun {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_MODEL_RUNS_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_MODEL_RUNS_GEN",
			sequenceName = "SEQ_AI_MODEL_RUNS",
			allocationSize = 1
	)
	@Column(name = "MODEL_RUN_ID")
	private Long modelRunId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "USER_ID")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "PLAN_ID")
	private TravelPlan travelPlan;

	@Column(name = "REQUEST_ID", length = 64)
	private String requestId;

	@Column(
			name = "MODEL_DOMAIN",
			nullable = false,
			length = 40
	)
	private String modelDomain;

	@Column(
			name = "MODEL_NAME",
			nullable = false,
			length = 100
	)
	private String modelName;

	@Column(
			name = "MODEL_VERSION",
			nullable = false,
			length = 150
	)
	private String modelVersion;

	@Column(
			name = "POLICY_VERSION",
			length = 150
	)
	private String policyVersion;

	@Column(
			name = "CONTEXT_MODE",
			length = 30
	)
	private String contextMode;

	@Column(name = "INPUT_TURN_COUNT")
	private Integer inputTurnCount;

	@Column(name = "TURNS_USED")
	private Integer turnsUsed;

	@Column(
			name = "FALLBACK_TRIGGERED",
			nullable = false,
			length = 1,
			columnDefinition = "CHAR(1)"
	)
	@Builder.Default
	private String fallbackTriggered = "N";

	@Column(
			name = "FIRST2_MARGIN",
			precision = 10,
			scale = 8
	)
	private BigDecimal first2Margin;

	@Column(
			name = "CONFIDENCE",
			precision = 10,
			scale = 8
	)
	private BigDecimal confidence;

	@Column(
			name = "LATENCY_MS",
			precision = 12,
			scale = 3
	)
	private BigDecimal latencyMs;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "STATUS",
			nullable = false,
			length = 20
	)
	private AiModelRunStatus status;

	@Column(
			name = "ERROR_CODE",
			length = 100
	)
	private String errorCode;

	@Column(
			name = "ERROR_MESSAGE",
			length = 1000
	)
	private String errorMessage;

	// AI 모델 호출을 실제로 시작한 시각.
	// Service가 FastAPI 호출 직전에 시간을 기록해서 넘겨준다.
	@Column(
			name = "STARTED_AT",
			nullable = false,
			updatable = false
	)
	private LocalDateTime startedAt;

	@Column(name = "COMPLETED_AT")
	private LocalDateTime completedAt;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;

	public void completeSuccess() {
		this.status =
				AiModelRunStatus.SUCCESS;
		this.completedAt =
				AiTimeProvider.nowInServiceZone();
		this.errorCode =
				null;
		this.errorMessage =
				null;
	}


	public void completeFailure(
			String errorCode,
			String errorMessage
	) {

		this.status =
				AiModelRunStatus.FAILED;
		this.completedAt =
				AiTimeProvider.nowInServiceZone();
		this.errorCode =
				errorCode;
		this.errorMessage =
				errorMessage;
	}


	/**
	 * startedAt을 Service에서 넣지 못했을 경우의
	 * 마지막 안전장치.
	 */
	@PrePersist
	protected void ensureStartedAt() {

		if (
				startedAt == null
		) {
			startedAt =
					AiTimeProvider.nowInServiceZone();
		}
	}
}