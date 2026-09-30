package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "AI_INSIGHTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiInsight {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_INSIGHTS_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_INSIGHTS_GEN",
			sequenceName = "SEQ_AI_INSIGHTS",
			allocationSize = 1
	)
	@Column(name = "INSIGHT_ID")
	private Long insightId;

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
			name = "INSIGHT_KEY",
			nullable = false,
			length = 120
	)
	private String insightKey;

	@Column(
			name = "INSIGHT_TYPE",
			nullable = false,
			length = 40
	)
	private String insightType;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "PRIORITY",
			nullable = false,
			length = 20
	)
	@Builder.Default
	private AiInsightPriority priority =
			AiInsightPriority.MEDIUM;

	@Column(
			name = "TITLE",
			nullable = false,
			length = 200
	)
	private String title;

	@Lob
	@Column(
			name = "CONTENT",
			nullable = false
	)
	private String content;

	@Lob
	@Column(name = "EVIDENCE_JSON")
	private String evidenceJson;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "STATUS",
			nullable = false,
			length = 20
	)
	@Builder.Default
	private AiInsightStatus status =
			AiInsightStatus.ACTIVE;

	@Column(
			name = "VALID_FROM",
			insertable = false,
			updatable = false
	)
	private LocalDateTime validFrom;

	@Column(name = "VALID_UNTIL")
	private LocalDateTime validUntil;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;

	@Column(
			name = "UPDATED_AT",
			insertable = false
	)
	private LocalDateTime updatedAt;

	public void dismiss() {
		this.status =
				AiInsightStatus.DISMISSED;

		this.updatedAt =
				LocalDateTime.now();
	}

	public void expire() {
		this.status =
				AiInsightStatus.EXPIRED;

		this.validUntil =
				LocalDateTime.now();

		this.updatedAt =
				LocalDateTime.now();
	}

	public void supersede() {
		this.status =
				AiInsightStatus.SUPERSEDED;

		this.validUntil =
				LocalDateTime.now();

		this.updatedAt =
				LocalDateTime.now();
	}
}