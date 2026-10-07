package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "AI_TRAVEL_CONTEXT_SNAPSHOTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTravelContextSnapshot {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_TRAVEL_CONTEXTS_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_TRAVEL_CONTEXTS_GEN",
			sequenceName = "SEQ_AI_TRAVEL_CONTEXTS",
			allocationSize = 1
	)
	@Column(name = "CONTEXT_ID")
	private Long contextId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "USER_ID",
			nullable = false
	)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "PLAN_ID",
			nullable = false
	)
	private TravelPlan travelPlan;

	@Column(
			name = "PLAN_NAME",
			nullable = false,
			length = 100
	)
	private String planName;

	@Column(
			name = "LOCATION_CD",
			length = 20
	)
	private String locationCd;

	@Column(
			name = "COMPANION_TYPE",
			length = 30
	)
	private String companionType;

	@Column(
			name = "TOTAL_BUDGET",
			precision = 18,
			scale = 2
	)
	private BigDecimal totalBudget;

	@Column(name = "START_DATE")
	private LocalDateTime startDate;

	@Column(name = "END_DATE")
	private LocalDateTime endDate;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "TRAVEL_PHASE",
			nullable = false,
			length = 20
	)
	private AiTravelPhase travelPhase;

	@Lob
	@Column(name = "ROUTE_CONTEXT_JSON")
	private String routeContextJson;

	@Lob
	@Column(name = "QUEST_CONTEXT_JSON")
	private String questContextJson;

	@Lob
	@Column(name = "RECOMMENDATION_CONTEXT_JSON")
	private String recommendationContextJson;

	@Lob
	@Column(name = "DIARY_CONTEXT_JSON")
	private String diaryContextJson;

	@Column(
			name = "CONTEXT_HASH",
			length = 64
	)
	private String contextHash;

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