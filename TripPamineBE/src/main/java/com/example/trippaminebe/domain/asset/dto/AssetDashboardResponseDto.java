package com.example.trippaminebe.domain.asset.dto;

import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
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

    // USER_ACCOUNTS의 ACTIVE 계좌 BALANCE 합계
    private Long totalAsset;

    // TRANSACTIONS income 합계
    private Long totalIncome;

    // TRANSACTIONS expense 합계
    private Long totalExpense;

    // 현재는 Snapshot 사용
    private Long travelExpense;

    private LocalDate snapshotDate;


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

        private LocalDate date;

        private Long totalAsset;

        private Long totalIncome;

        private Long totalExpense;


        public static AssetHistoryInfo from(
                AssetSnapshot snapshot
        ) {

            return new AssetHistoryInfo(

                    snapshot.getSnapshotDate(),

                    snapshot.getTotalAsset(),

                    snapshot.getTotalIncome(),

                    snapshot.getTotalExpense()
            );
        }
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

            // ======================================
            // USER_ACCOUNTS에서 계산된 현재 총자산
            // ======================================

            Long totalAsset,

            AssetSnapshot latestSnapshot,

            List<AssetSnapshot> snapshots,

            List<MonthlyBudget> monthlyBudgets,

            List<AssetGoal> assetGoals,

            Long totalIncome,

            Long totalExpense,

            Map<String, Long> categoryExpenseMap

    ) {

        // ======================================
        // 기본값
        // ======================================

        Long travelExpense = 0L;

        LocalDate snapshotDate = null;


        // ======================================
        // totalAsset null 방어
        // ======================================

        if (totalAsset == null) {

            totalAsset = 0L;
        }


        // ======================================
        // 최신 Snapshot
        //
        // 중요:
        // totalAsset은 Snapshot에서 가져오지 않는다.
        //
        // Snapshot은 현재
        // travelExpense / snapshotDate에만 사용한다.
        // ======================================

        if (latestSnapshot != null) {

            travelExpense =
                    latestSnapshot.getTravelExpense();

            snapshotDate =
                    latestSnapshot.getSnapshotDate();
        }


        // ======================================
        // 수입 null 방어
        // ======================================

        if (totalIncome == null) {

            totalIncome = 0L;
        }


        // ======================================
        // 지출 null 방어
        // ======================================

        if (totalExpense == null) {

            totalExpense = 0L;
        }


        // ======================================
        // 자산 변화
        // ======================================

        List<AssetHistoryInfo> assetHistory =

                snapshots.stream()

                        .map(
                                AssetHistoryInfo::from
                        )

                        .toList();


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

                monthlyBudgets.stream()

                        .map(
                                BudgetInfo::from
                        )

                        .toList();


        // ======================================
        // 목표
        // ======================================

        List<GoalInfo> goals =

                assetGoals.stream()

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

                travelExpense,

                snapshotDate,

                assetHistory,

                categoryExpenses,

                budgets,

                goals
        );
    }
}