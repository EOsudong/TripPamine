package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
		name = "AI_USER_FACTS",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "UQ_AUF_USER_KEY",
						columnNames = {
								"USER_ID",
								"FACT_KEY"
						}
				)
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUserFact {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_USER_FACTS_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_USER_FACTS_GEN",
			sequenceName = "SEQ_AI_USER_FACTS",
			allocationSize = 1
	)
	@Column(name = "FACT_ID")
	private Long factId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "USER_ID",
			nullable = false
	)
	private User user;

	@Column(
			name = "FACT_KEY",
			nullable = false,
			length = 120
	)
	private String factKey;

	@Lob
	@Column(
			name = "FACT_VALUE_JSON",
			nullable = false
	)
	private String factValueJson;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "SOURCE_TYPE",
			nullable = false,
			length = 30
	)
	private AiUserFactSourceType sourceType;

	@Column(
			name = "CONFIDENCE",
			precision = 10,
			scale = 8
	)
	private BigDecimal confidence;

	@Column(
			name = "EVIDENCE_COUNT",
			nullable = false
	)
	@Builder.Default
	private Integer evidenceCount = 0;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "STATUS",
			nullable = false,
			length = 20
	)
	@Builder.Default
	private AiUserFactStatus status =
			AiUserFactStatus.ACTIVE;

	@Column(
			name = "FIRST_OBSERVED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime firstObservedAt;

	@Column(
			name = "LAST_OBSERVED_AT",
			insertable = false
	)
	private LocalDateTime lastObservedAt;

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

	public void updateFact(
			String factValueJson,
			AiUserFactSourceType sourceType,
			BigDecimal confidence
	) {

		LocalDateTime now =
				AiTimeProvider
						.nowInServiceZone();


		this.factValueJson =
				factValueJson;

		this.sourceType =
				sourceType;

		this.confidence =
				confidence;

		this.lastObservedAt =
				now;

		this.updatedAt =
				now;
	}


	public void incrementEvidenceCount() {

		LocalDateTime now =
				AiTimeProvider
						.nowInServiceZone();


		this.evidenceCount =
				this.evidenceCount == null
						? 1
						: this.evidenceCount + 1;


		/*
		 * 새로운 Evidence가 하나 더 관찰됐으므로
		 * 마지막 관찰 시각도 함께 갱신한다.
		 */
		this.lastObservedAt =
				now;

		this.updatedAt =
				now;
	}

	public void dispute() {

		this.status =
				AiUserFactStatus.DISPUTED;

		this.updatedAt =
				AiTimeProvider
						.nowInServiceZone();
	}


	public void retire() {

		this.status =
				AiUserFactStatus.RETIRED;

		this.updatedAt =
				AiTimeProvider
						.nowInServiceZone();
	}


	public void activate() {

		this.status =
				AiUserFactStatus.ACTIVE;

		this.updatedAt =
				AiTimeProvider
						.nowInServiceZone();
	}
}