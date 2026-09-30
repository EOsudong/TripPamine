package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "AI_FINANCE_SUMMARIES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiFinanceSummary {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_FINANCE_SUMMARIES_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_FINANCE_SUMMARIES_GEN",
			sequenceName = "SEQ_AI_FINANCE_SUMMARIES",
			allocationSize = 1
	)
	@Column(name = "SUMMARY_ID")
	private Long summaryId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "USER_ID",
			nullable = false
	)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "PLAN_ID")
	private TravelPlan travelPlan;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "PERIOD_TYPE",
			nullable = false,
			length = 20
	)
	private AiFinancePeriodType periodType;

	@Column(name = "PERIOD_START", nullable = false)
	private LocalDateTime periodStart;

	@Column(name = "PERIOD_END", nullable = false)
	private LocalDateTime periodEnd;

	@Column(
			name = "TOTAL_INCOME",
			nullable = false,
			precision = 18,
			scale = 2
	)
	@Builder.Default
	private BigDecimal totalIncome = BigDecimal.ZERO;

	@Column(
			name = "TOTAL_EXPENSE",
			nullable = false,
			precision = 18,
			scale = 2
	)
	@Builder.Default
	private BigDecimal totalExpense = BigDecimal.ZERO;

	@Column(
			name = "NET_AMOUNT",
			nullable = false,
			precision = 18,
			scale = 2
	)
	@Builder.Default
	private BigDecimal netAmount = BigDecimal.ZERO;

	@Column(
			name = "TRAVEL_BUDGET",
			precision = 18,
			scale = 2
	)
	private BigDecimal travelBudget;

	@Column(
			name = "TRAVEL_SPENT",
			precision = 18,
			scale = 2
	)
	private BigDecimal travelSpent;

	@Column(
			name = "BUDGET_REMAINING",
			precision = 18,
			scale = 2
	)
	private BigDecimal budgetRemaining;

	@Column(
			name = "TOP_CATEGORY",
			length = 50
	)
	private String topCategory;

	@Lob
	@Column(name = "CATEGORY_SUMMARY_JSON")
	private String categorySummaryJson;

	@Lob
	@Column(name = "ACCOUNT_SUMMARY_JSON")
	private String accountSummaryJson;

	@Column(
			name = "SOURCE_RECORD_COUNT",
			nullable = false
	)
	@Builder.Default
	private Integer sourceRecordCount = 0;

	@Column(
			name = "AS_OF_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime asOfAt;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;
}