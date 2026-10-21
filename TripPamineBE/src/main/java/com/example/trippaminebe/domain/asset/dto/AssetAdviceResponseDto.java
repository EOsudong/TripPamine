package com.example.trippaminebe.domain.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetAdviceResponseDto {

    // =========================================
    // 1. 사용자의 월간 자산 내역
    // =========================================

    // 월평균 수입
    private Long monthlyAverageIncome;

    // 월평균 지출
    private Long monthlyAverageExpense;

    // 월평균 여유자금
    // 월평균 수입 - 월평균 지출
    private Long monthlySurplus;


    // =========================================
    // 2. 여행 목표
    // =========================================

    private TravelGoalInfo travelGoal;


    // =========================================
    // 3. 자산관리 상태
    // =========================================

    /*
     * 예:
     *
     * ACHIEVABLE
     * TIGHT
     * SHORTAGE
     * COMPLETED
     * NO_GOAL
     */
    private String financialStatus;


    // =========================================
    // 4. 사용자에게 보여줄 추천 목록
    // =========================================

    private List<RecommendationInfo> recommendations;


    // =========================================
    // 여행 목표 정보
    // =========================================

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TravelGoalInfo {

        // 목표 ID
        private Long goalId;

        // 예: 일본 도쿄 여행
        private String goalName;

        // 목표 금액
        private Long targetAmount;

        // 현재까지 모은 금액
        private Long currentAmount;

        // 앞으로 필요한 금액
        private Long remainingAmount;

        // 목표 달성률
        // 예: 40.0
        private Double progressRate;

        // 목표 날짜
        private LocalDate targetDate;

        // 오늘부터 목표일까지 남은 개월 수
        private Long remainingMonths;

        // 목표일까지 매월 필요한 저축액
        private Long requiredMonthlySaving;
    }


    // =========================================
    // 추천 정보
    // =========================================

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationInfo {

        /*
         * SAVING
         * BUDGET
         * EXPENSE
         * GOAL
         * FINANCE
         */
        private String category;

        // 추천 제목
        private String title;

        // 사용자에게 보여줄 설명
        private String description;

        /*
         * HIGH
         * MEDIUM
         * LOW
         */
        private String priority;
    }
}