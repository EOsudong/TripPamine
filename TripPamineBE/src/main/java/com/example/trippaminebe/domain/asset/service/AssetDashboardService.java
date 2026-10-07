package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.entity.LinkStatus;
import com.example.trippaminebe.domain.account.repository.AccountRepository;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;

import com.example.trippaminebe.domain.asset.dto.AssetDashboardResponseDto;
import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;
import com.example.trippaminebe.domain.asset.repository.AssetGoalRepository;
import com.example.trippaminebe.domain.asset.repository.AssetSnapshotRepository;
import com.example.trippaminebe.domain.asset.repository.MonthlyBudgetRepository;

import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Transactional
public class AssetDashboardService {

    private final AssetSnapshotRepository assetSnapshotRepository;

    private final MonthlyBudgetRepository monthlyBudgetRepository;

    private final AssetGoalRepository assetGoalRepository;

    private final AccountBookRepository accountBookRepository;

    private final UserRepository userRepository;

    private final AccountRepository accountRepository;


    // =====================================================
    // Dashboard
    // =====================================================

    public AssetDashboardResponseDto getDashboard(
            Long userId,
            Integer year,
            Integer month
    ) {

        // =========================================
        // 1. 사용자
        // =========================================

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "사용자를 찾을 수 없습니다. userId="
                                                        + userId
                                        )
                        );


        String username =
                user.getUserName();


        // =========================================
        // 2. 조회 년월
        // =========================================

        YearMonth targetYearMonth;

        try {

            targetYearMonth =
                    YearMonth.of(
                            year,
                            month
                    );

        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "조회 년월이 올바르지 않습니다."
            );
        }


        LocalDate monthStart =
                targetYearMonth.atDay(1);


        LocalDate monthEnd =
                targetYearMonth.atEndOfMonth();


        LocalDateTime startDate =
                monthStart.atStartOfDay();


        LocalDateTime endDate =
                targetYearMonth
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay();


        // =========================================
        // 3. 월 거래 조회
        // =========================================

        List<TransactionEntity> transactions =
                accountBookRepository
                        .findByUsernameAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                                username,
                                startDate,
                                endDate
                        );


        // =========================================
        // 4. 총 수입
        // =========================================

        long totalIncome =
                transactions.stream()

                        .filter(
                                transaction ->
                                        "income".equalsIgnoreCase(
                                                transaction.getType()
                                        )
                        )

                        .mapToLong(
                                transaction ->
                                        safeAmount(
                                                transaction.getAmount()
                                        )
                        )

                        .sum();


        // =========================================
        // 5. 총 지출
        // =========================================

        long totalExpense =
                transactions.stream()

                        .filter(
                                transaction ->
                                        "expense".equalsIgnoreCase(
                                                transaction.getType()
                                        )
                        )

                        .mapToLong(
                                transaction ->
                                        safeAmount(
                                                transaction.getAmount()
                                        )
                        )

                        .sum();


        // =========================================
        // 6. 카테고리별 지출
        // =========================================

        Map<String, Long> categoryExpenseMap =
                new LinkedHashMap<>();


        transactions.stream()

                .filter(
                        transaction ->
                                "expense".equalsIgnoreCase(
                                        transaction.getType()
                                )
                )

                .forEach(transaction -> {

                    String category =
                            transaction.getCategory();


                    if (
                            category == null
                                    ||
                                    category.isBlank()
                    ) {

                        category = "ETC";
                    }


                    category =
                            category.toUpperCase();


                    long amount =
                            safeAmount(
                                    transaction.getAmount()
                            );


                    categoryExpenseMap.merge(
                            category,
                            amount,
                            Long::sum
                    );
                });


        // =========================================
        // 7. ACTIVE 계좌
        // =========================================

        List<Account> activeAccounts =
                accountRepository
                        .findByUser_IdAndLinkStatus(
                                userId,
                                LinkStatus.ACTIVE
                        );


        // =========================================
        // 8. 현재 총자산
        // =========================================

        BigDecimal totalAssetDecimal =
                activeAccounts.stream()

                        .map(
                                Account::getBalance
                        )

                        .filter(
                                balance ->
                                        balance != null
                        )

                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        long totalAsset =
                totalAssetDecimal.longValue();


        // =========================================
        // 9. 오늘
        // =========================================

        LocalDate today =
                LocalDate.now();


        YearMonth currentYearMonth =
                YearMonth.from(today);


        // =========================================
        // 10. 현재 월일 경우 Snapshot 저장
        // =========================================

        if (
                targetYearMonth.equals(
                        currentYearMonth
                )
        ) {

            saveOrUpdateTodaySnapshot(

                    userId,

                    today,

                    totalAsset,

                    totalIncome,

                    totalExpense
            );
        }


        // =========================================
        // 11. 날짜별 수입 / 지출
        // =========================================

        Map<LocalDate, Long> dailyIncomeMap =
                new LinkedHashMap<>();


        Map<LocalDate, Long> dailyExpenseMap =
                new LinkedHashMap<>();


        for (
                TransactionEntity transaction
                : transactions
        ) {

            if (
                    transaction.getTransactionDate()
                            == null
            ) {

                continue;
            }


            LocalDate transactionDate =
                    transaction
                            .getTransactionDate()
                            .toLocalDate();


            long amount =
                    safeAmount(
                            transaction.getAmount()
                    );


            if (
                    "income".equalsIgnoreCase(
                            transaction.getType()
                    )
            ) {

                dailyIncomeMap.merge(
                        transactionDate,
                        amount,
                        Long::sum
                );

            } else if (
                    "expense".equalsIgnoreCase(
                            transaction.getType()
                    )
            ) {

                dailyExpenseMap.merge(
                        transactionDate,
                        amount,
                        Long::sum
                );
            }
        }


        // =========================================
        // 12. 자산 흐름 생성
        // =========================================

        List<AssetDashboardResponseDto.AssetHistoryInfo>
                assetHistory =
                buildMonthlyAssetHistory(

                        targetYearMonth,

                        today,

                        totalAsset,

                        dailyIncomeMap,

                        dailyExpenseMap
                );


        // =========================================
        // 13. 시작 자산 계산
        // =========================================
        //
        // 현재자산
        // - 이번 달 수입
        // + 이번 달 지출
        //
        // =========================================

        long startAsset =
                totalAsset
                        - totalIncome
                        + totalExpense;


        // =========================================
        // 14. 자산 증감액
        // =========================================

        long assetChangeAmount =
                totalAsset
                        - startAsset;


        // =========================================
        // 15. 자산 증감률
        // =========================================

        double assetChangeRate =
                calculateAssetChangeRate(

                        startAsset,

                        totalAsset
                );


        // =========================================
        // 16. 월 예산
        // =========================================

        List<MonthlyBudget> monthlyBudgets =
                monthlyBudgetRepository
                        .findByUserIdAndBudgetYearAndBudgetMonth(

                                userId,

                                year,

                                month
                        );


        // =========================================
        // 17. 자산 목표
        // =========================================

        List<AssetGoal> assetGoals =
                assetGoalRepository
                        .findByUserId(
                                userId
                        );


        // =========================================
        // 로그
        // =========================================

        System.out.println(
                "================================="
        );

        System.out.println(
                "[Asset Dashboard]"
        );

        System.out.println(
                "userId = " + userId
        );

        System.out.println(
                "조회 년월 = "
                        + year
                        + "-"
                        + month
        );

        System.out.println(
                "현재 총자산 = "
                        + totalAsset
        );

        System.out.println(
                "월 시작자산 = "
                        + startAsset
        );

        System.out.println(
                "월 수입 = "
                        + totalIncome
        );

        System.out.println(
                "월 지출 = "
                        + totalExpense
        );

        System.out.println(
                "자산 증감액 = "
                        + assetChangeAmount
        );

        System.out.println(
                "자산 증감률 = "
                        + assetChangeRate
                        + "%"
        );

        System.out.println(
                "그래프 데이터 수 = "
                        + assetHistory.size()
        );

        System.out.println(
                "================================="
        );


        // =========================================
        // 18. 반환
        // =========================================

        return AssetDashboardResponseDto.of(

                userId,

                year,

                month,

                totalAsset,

                startAsset,

                assetChangeAmount,

                assetChangeRate,

                assetHistory,

                monthlyBudgets,

                assetGoals,

                totalIncome,

                totalExpense,

                categoryExpenseMap
        );
    }


    // =====================================================
    // Snapshot
    // =====================================================

    private void saveOrUpdateTodaySnapshot(

            Long userId,

            LocalDate today,

            Long totalAsset,

            Long totalIncome,

            Long totalExpense
    ) {

        AssetSnapshot todaySnapshot =
                assetSnapshotRepository
                        .findByUserIdAndSnapshotDate(

                                userId,

                                today
                        )

                        .orElse(null);


        if (todaySnapshot == null) {

            todaySnapshot =
                    AssetSnapshot.builder()

                            .userId(userId)

                            .snapshotDate(today)

                            .totalAsset(totalAsset)

                            .totalIncome(totalIncome)

                            .totalExpense(totalExpense)

                            // 기존 DB 구조와 호환을 위해 유지
                            // Dashboard에서는 사용하지 않음
                            .travelExpense(0L)

                            .build();


            assetSnapshotRepository.save(
                    todaySnapshot
            );


            return;
        }


        todaySnapshot.setTotalAsset(
                totalAsset
        );


        todaySnapshot.setTotalIncome(
                totalIncome
        );


        todaySnapshot.setTotalExpense(
                totalExpense
        );


        assetSnapshotRepository.save(
                todaySnapshot
        );
    }


    // =====================================================
    // 월간 자산 History
    // =====================================================

    private List<AssetDashboardResponseDto.AssetHistoryInfo>
    buildMonthlyAssetHistory(

            YearMonth targetYearMonth,

            LocalDate today,

            Long currentTotalAsset,

            Map<LocalDate, Long> dailyIncomeMap,

            Map<LocalDate, Long> dailyExpenseMap
    ) {

        List<AssetDashboardResponseDto.AssetHistoryInfo>
                history =
                new ArrayList<>();


        LocalDate monthStart =
                targetYearMonth.atDay(1);


        LocalDate monthEnd =
                targetYearMonth.atEndOfMonth();


        YearMonth currentYearMonth =
                YearMonth.from(today);


        LocalDate graphEndDate;


        // =========================================
        // 현재 월
        // =========================================

        if (
                targetYearMonth.equals(
                        currentYearMonth
                )
        ) {

            graphEndDate =
                    today.isBefore(monthEnd)
                            ? today
                            : monthEnd;


            // =========================================
            // 과거 월
            // =========================================

        } else if (
                targetYearMonth.isBefore(
                        currentYearMonth
                )
        ) {

            graphEndDate =
                    monthEnd;


            // =========================================
            // 미래 월
            // =========================================

        } else {

            return history;
        }


        // =========================================
        // 그래프 종료일까지 누적 거래
        // =========================================

        long incomeUntilGraphEnd =
                0L;


        long expenseUntilGraphEnd =
                0L;


        LocalDate calculateDate =
                monthStart;


        while (
                !calculateDate.isAfter(
                        graphEndDate
                )
        ) {

            incomeUntilGraphEnd +=

                    dailyIncomeMap
                            .getOrDefault(
                                    calculateDate,
                                    0L
                            );


            expenseUntilGraphEnd +=

                    dailyExpenseMap
                            .getOrDefault(
                                    calculateDate,
                                    0L
                            );


            calculateDate =
                    calculateDate.plusDays(1);
        }


        // =========================================
        // 월 시작 직전 자산
        // =========================================

        long runningAsset =
                currentTotalAsset
                        - incomeUntilGraphEnd
                        + expenseUntilGraphEnd;


        long cumulativeIncome =
                0L;


        long cumulativeExpense =
                0L;


        LocalDate currentDate =
                monthStart;


        // =========================================
        // 날짜별 자산 생성
        // =========================================

        while (
                !currentDate.isAfter(
                        graphEndDate
                )
        ) {

            long dailyIncome =
                    dailyIncomeMap
                            .getOrDefault(
                                    currentDate,
                                    0L
                            );


            long dailyExpense =
                    dailyExpenseMap
                            .getOrDefault(
                                    currentDate,
                                    0L
                            );


            cumulativeIncome +=
                    dailyIncome;


            cumulativeExpense +=
                    dailyExpense;


            runningAsset +=
                    dailyIncome;


            runningAsset -=
                    dailyExpense;


            history.add(

                    new AssetDashboardResponseDto.AssetHistoryInfo(

                            currentDate,

                            runningAsset,

                            cumulativeIncome,

                            cumulativeExpense
                    )
            );


            currentDate =
                    currentDate.plusDays(1);
        }


        return history;
    }


    // =====================================================
    // 자산 증감률
    // =====================================================

    private double calculateAssetChangeRate(

            long startAsset,

            long currentAsset
    ) {

        if (startAsset == 0L) {

            return 0.0;
        }


        double changeRate =

                (
                        (double)
                                (
                                        currentAsset
                                                - startAsset
                                )

                                / startAsset
                )

                        * 100.0;


        // 소수점 첫째 자리
        return Math.round(
                changeRate * 10.0
        )
                / 10.0;
    }


    // =====================================================
    // null 금액 방어
    // =====================================================

    private long safeAmount(
            Long amount
    ) {

        return amount != null
                ? amount
                : 0L;
    }
// =====================================================
// 기간별 자산 흐름 조회
//
// WEEK    : 최근 7일
// MONTH   : 최근 1개월
// QUARTER : 최근 3개월
// YEAR    : 최근 1년
// =====================================================

    @Transactional(readOnly = true)
    public List<AssetDashboardResponseDto.AssetHistoryInfo>
    getAssetHistoryByPeriod(
            Long userId,
            String period
    ) {

        // =========================================
        // 1. 기간 기본값
        // =========================================

        String normalizedPeriod =
                period == null || period.isBlank()
                        ? "MONTH"
                        : period.trim().toUpperCase();


        // =========================================
        // 2. 종료 날짜
        // =========================================

        LocalDate endDate =
                LocalDate.now();


        // =========================================
        // 3. 시작 날짜 계산
        // =========================================

        LocalDate startDate;

        switch (normalizedPeriod) {

            case "WEEK" ->

                    startDate =
                            endDate.minusDays(6);


            case "MONTH" ->

                    startDate =
                            endDate.minusMonths(1)
                                    .plusDays(1);


            case "QUARTER" ->

                    startDate =
                            endDate.minusMonths(3)
                                    .plusDays(1);


            case "YEAR" ->

                    startDate =
                            endDate.minusYears(1)
                                    .plusDays(1);


            default ->

                    throw new IllegalArgumentException(
                            "지원하지 않는 조회 기간입니다. "
                                    + "WEEK, MONTH, QUARTER, YEAR 중 하나를 사용해주세요."
                    );
        }


        // =========================================
        // 4. 기간 내 Snapshot 조회
        // =========================================

        List<AssetSnapshot> snapshots =
                assetSnapshotRepository
                        .findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                                userId,
                                startDate,
                                endDate
                        );


        // =========================================
        // 5. Snapshot -> AssetHistoryInfo 변환
        // =========================================

        List<AssetDashboardResponseDto.AssetHistoryInfo>
                history =
                snapshots
                        .stream()

                        .map(
                                snapshot ->
                                        new AssetDashboardResponseDto.AssetHistoryInfo(

                                                snapshot.getSnapshotDate(),

                                                snapshot.getTotalAsset() != null
                                                        ? snapshot.getTotalAsset()
                                                        : 0L,

                                                snapshot.getTotalIncome() != null
                                                        ? snapshot.getTotalIncome()
                                                        : 0L,

                                                snapshot.getTotalExpense() != null
                                                        ? snapshot.getTotalExpense()
                                                        : 0L
                                        )
                        )

                        .toList();


        // =========================================
        // 6. 개발 확인용 로그
        // =========================================

        System.out.println(
                "========================================="
        );

        System.out.println(
                "[Asset History]"
        );

        System.out.println(
                "userId = " + userId
        );

        System.out.println(
                "period = " + normalizedPeriod
        );

        System.out.println(
                "startDate = " + startDate
        );

        System.out.println(
                "endDate = " + endDate
        );

        System.out.println(
                "history size = " + history.size()
        );

        System.out.println(
                "========================================="
        );


        // =========================================
        // 7. 반환
        // =========================================

        return history;
    }
}