package com.example.trippaminebe.domain.asset.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "MONTHLY_BUDGETS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBudget {

    // ========================================
    // PK - 예산 ID
    // ========================================
    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "monthly_budget_seq"
    )
    @SequenceGenerator(
            name = "monthly_budget_seq",
            sequenceName = "SEQ_MONTHLY_BUDGETS",
            allocationSize = 1
    )
    @Column(name = "BUDGET_ID")
    private Long budgetId;


    // ========================================
    // 사용자 ID
    // USERS.USER_ID를 참조
    // ========================================
    @Column(name = "USER_ID", nullable = false)
    private Long userId;


    // ========================================
    // 예산 연도
    // 예: 2026
    // ========================================
    @Column(name = "BUDGET_YEAR", nullable = false)
    private Integer budgetYear;


    // ========================================
    // 예산 월
    // 예: 9
    // ========================================
    @Column(name = "BUDGET_MONTH", nullable = false)
    private Integer budgetMonth;


    // ========================================
    // 카테고리 ID
    // 예: FOOD, TRANSPORT, TRAVEL
    // ========================================
    @Column(name = "CATEGORY_ID", nullable = false, length = 20)
    private String categoryId;


    // ========================================
    // 카테고리 이름
    // 예: 식비, 교통비, 여행비
    // ========================================
    @Column(name = "CATEGORY_NAME", nullable = false, length = 50)
    private String categoryName;


    // ========================================
    // 예산 금액
    // ========================================
    @Column(name = "BUDGET_AMOUNT", nullable = false)
    private Long budgetAmount;


    // ========================================
    // 생성일
    // ========================================
    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;


    // ========================================
    // 수정일
    // ========================================
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;


    // ========================================
    // INSERT 직전에 자동 실행
    // ========================================
    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }

        if (budgetAmount == null) {
            budgetAmount = 0L;
        }
    }


    // ========================================
    // UPDATE 직전에 자동 실행
    // ========================================
    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }
}