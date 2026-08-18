package com.example.trippaminebe.domain.travel.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRAVEL_PLANS")
@Getter
@Setter
@NoArgsConstructor
public class TravelPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "travelPlanSeq")
    @SequenceGenerator(name = "travelPlanSeq", sequenceName = "SEQ_TRAVEL_PLANS", allocationSize = 1)
    @Column(name = "PLAN_ID")
    private Long planId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "PLAN_NAME", nullable = false, length = 100)
    private String planName;

    @Column(name = "TOTAL_BUDGET", nullable = false)
    private BigDecimal totalBudget;

    @Column(name = "COMPANION_TYPE", length = 30)
    private String companionType;

    // 💡 [추가] DB NOT NULL 제약조건 대응용 지역 코드
    @Column(name = "LOCATION_CD", length = 20)
    private String locationCd;

    @Column(name = "BLIND_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String blindYn = "N";

    @Column(name = "START_DATE")
    private LocalDateTime startDate;

    @Column(name = "END_DATE")
    private LocalDateTime endDate;

    @Column(name = "DEL_YN", nullable = false, columnDefinition = "CHAR(1)")
    private String delYn = "N";
}