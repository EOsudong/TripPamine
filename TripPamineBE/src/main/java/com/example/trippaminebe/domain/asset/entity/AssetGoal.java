package com.example.trippaminebe.domain.asset.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ASSET_GOALS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetGoal {

    // ========================================
    // 목표 ID (PK)
    // ========================================
    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "asset_goal_seq"
    )
    @SequenceGenerator(
            name = "asset_goal_seq",
            sequenceName = "SEQ_ASSET_GOALS",
            allocationSize = 1
    )
    @Column(name = "GOAL_ID")
    private Long goalId;


    // ========================================
    // 사용자 ID
    // USERS.USER_ID 참조
    // ========================================
    @Column(name = "USER_ID", nullable = false)
    private Long userId;


    // ========================================
    // 목표 이름
    // 예: 일본 여행자금
    // ========================================
    @Column(name = "GOAL_NAME", nullable = false, length = 100)
    private String goalName;


    // ========================================
    // 목표 금액
    // ========================================
    @Column(name = "TARGET_AMOUNT", nullable = false)
    private Long targetAmount;


    // ========================================
    // 현재까지 모은 금액
    // ========================================
    @Column(name = "CURRENT_AMOUNT")
    private Long currentAmount;


    // ========================================
    // 목표 날짜
    // ========================================
    @Column(name = "TARGET_DATE")
    private LocalDate targetDate;


    // ========================================
    // 목표 유형
    // 예: TRAVEL, SAVING, EMERGENCY
    // ========================================
    @Column(name = "GOAL_TYPE", length = 20)
    private String goalType;


    // ========================================
    // 목표 상태
    // ACTIVE / COMPLETED / CANCELLED
    // ========================================
    @Column(name = "STATUS", length = 20)
    private String status;


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

        if (currentAmount == null) {
            currentAmount = 0L;
        }

        if (status == null) {
            status = "ACTIVE";
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
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