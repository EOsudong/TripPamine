package com.example.trippaminebe.domain.asset.dto;

import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;


@Getter
@AllArgsConstructor
public class AssetDashboardResponseDto {

    // ==========================================
    // 기본 정보
    // ==========================================

    private Long userId;

    private Integer year;

    private Integer month;


    // ==========================================
    // 현재 자산
    // ==========================================

    private Long totalAsset;

    private Long totalIncome;

    private Long totalExpense;


    // ==========================================
    // 자산 증감
    // ==========================================

    // 이번 달 시작 자산
    private Long startAsset;

    // 현재자산 - 시작자산
    private Long assetChangeAmount;

    // 자산 증감률 (%)
    private Double assetChangeRate;


    // ==========================================
    // 자산 변화 그래프
    // ==========================================

    private List<AssetHistoryInfo> assetHistory;


    // ==========================================
    // 카테고리별 지출
    // ==========================================

    private List<CategoryExpenseInfo> categoryExpenses;


    // ==========================================
    // 예산
    // ==========================================

    private List<BudgetInfo> budgets;


    // ==========================================
    // 목표
    // ==========================================

    private List<GoalInfo> goals;


    // ==========================================
    // 자산 변화 DTO
    // ==========================================

    @Getter
    @AllArgsConstructor
    public static class AssetHistoryInfo {

        private LocalDate snapshotDate;

        private Long totalAsset;

        private Long totalIncome;

        private Long totalExpense;
    }


    // ==========================================
    // 카테고리별 지출 DTO
    // ==========================================

    @Getter
    @AllArgsConstructor
    public static class CategoryExpenseInfo {

        private String category;

        private Long amount;
    }


    // ==========================================
    // 예산 DTO
    // ==========================================

    @Getter
    @AllArgsConstructor
    public static class BudgetInfo {

        private Long budgetId;

        private String categoryId;

        private String categoryName;

        private Long budgetAmount;


        public static BudgetInfo from(
                MonthlyBudget budget
        ) {

            return new BudgetInfo(
                    budget.getBudgetId(),
                    budget.getCategoryId(),
                    budget.getCategoryName(),
                    budget.getBudgetAmount()
            );
        }
    }


    // ==========================================
    // 목표 DTO
    // ==========================================

    @Getter
    @AllArgsConstructor
    public static class GoalInfo {

        private Long goalId;

        private String goalName;

        private Long targetAmount;

        private Long currentAmount;

        private LocalDate targetDate;

        private String goalType;

        private String status;

        private Double progressRate;


        public static GoalInfo from(
                AssetGoal goal
        ) {

            double progressRate = 0.0;


            if (
                    goal.getTargetAmount() != null
                            &&
                            goal.getTargetAmount() > 0
                            &&
                            goal.getCurrentAmount() != null
            ) {

                progressRate =
                        (
                                (double) goal.getCurrentAmount()
                                        /
                                        goal.getTargetAmount()
                        )
                                * 100;
            }


            // 100% 초과 방지
            progressRate =
                    Math.min(
                            100.0,
                            progressRate
                    );


            progressRate =
                    Math.round(
                            progressRate * 10.0
                    )
                            / 10.0;


            return new GoalInfo(
                    goal.getGoalId(),
                    goal.getGoalName(),
                    goal.getTargetAmount(),
                    goal.getCurrentAmount(),
                    goal.getTargetDate(),
                    goal.getGoalType(),
                    goal.getStatus(),
                    progressRate
            );
        }
    }


    // ==========================================
    // 최종 Dashboard 생성
    // ==========================================

    public static AssetDashboardResponseDto of(

            Long userId,

            Integer year,

            Integer month,

            Long totalAsset,

            Long startAsset,

            Long assetChangeAmount,

            Double assetChangeRate,

            List<AssetHistoryInfo> assetHistory,

            List<MonthlyBudget> monthlyBudgets,

            List<AssetGoal> assetGoals,

            Long totalIncome,

            Long totalExpense,

            Map<String, Long> categoryExpenseMap

    ) {

        // ======================================
        // null 방어
        // ======================================

        if (totalAsset == null) {
            totalAsset = 0L;
        }

        if (startAsset == null) {
            startAsset = 0L;
        }

        if (assetChangeAmount == null) {
            assetChangeAmount = 0L;
        }

        if (assetChangeRate == null) {
            assetChangeRate = 0.0;
        }

        if (totalIncome == null) {
            totalIncome = 0L;
        }

        if (totalExpense == null) {
            totalExpense = 0L;
        }

        if (assetHistory == null) {
            assetHistory = List.of();
        }

        if (categoryExpenseMap == null) {
            categoryExpenseMap = Map.of();
        }

        if (monthlyBudgets == null) {
            monthlyBudgets = List.of();
        }

        if (assetGoals == null) {
            assetGoals = List.of();
        }


        // ======================================
        // 카테고리별 지출
        // ======================================

        List<CategoryExpenseInfo> categoryExpenses =

                categoryExpenseMap
                        .entrySet()
                        .stream()

                        .map(
                                entry ->
                                        new CategoryExpenseInfo(
                                                entry.getKey(),
                                                entry.getValue()
                                        )
                        )

                        .toList();


        // ======================================
        // 예산
        // ======================================

        List<BudgetInfo> budgets =

                monthlyBudgets
                        .stream()

                        .map(
                                BudgetInfo::from
                        )

                        .toList();


        // ======================================
        // 목표
        // ======================================

        List<GoalInfo> goals =

                assetGoals
                        .stream()

                        .map(
                                GoalInfo::from
                        )

                        .toList();


        // ======================================
        // 최종 Response
        // ======================================

        return new AssetDashboardResponseDto(

                userId,

                year,

                month,

                totalAsset,

                totalIncome,

                totalExpense,

                startAsset,

                assetChangeAmount,

                assetChangeRate,

                assetHistory,

                categoryExpenses,

                budgets,

                goals
        );
    }
}