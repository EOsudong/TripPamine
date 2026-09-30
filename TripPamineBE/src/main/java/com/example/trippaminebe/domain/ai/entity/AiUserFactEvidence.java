package com.example.trippaminebe.domain.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
		name = "AI_USER_FACT_EVIDENCE",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "UQ_AUFE_FACT_EVENT",
						columnNames = {
								"FACT_ID",
								"EVENT_ID"
						}
				)
		}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUserFactEvidence {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_USER_FACT_EVIDENCE_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_USER_FACT_EVIDENCE_GEN",
			sequenceName = "SEQ_AI_USER_FACT_EVIDENCE",
			allocationSize = 1
	)
	@Column(name = "EVIDENCE_ID")
	private Long evidenceId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "FACT_ID",
			nullable = false
	)
	private AiUserFact fact;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "EVENT_ID",
			nullable = false
	)
	private AiManagerEvent event;

	@Column(
			name = "WEIGHT",
			nullable = false,
			precision = 10,
			scale = 8
	)
	@Builder.Default
	private BigDecimal weight =
			BigDecimal.ONE;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;
}