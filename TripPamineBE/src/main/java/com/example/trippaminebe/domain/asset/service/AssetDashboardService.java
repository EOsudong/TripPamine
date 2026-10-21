package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.entity.LinkStatus;
import com.example.trippaminebe.domain.account.repository.AccountRepository;
import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.asset.dto.AssetDashboardResponseDto;
import com.example.trippaminebe.domain.asset.dto.AssetForecastResponseDto;
import com.example.trippaminebe.domain.asset.dto.AssetHistoryResponseDto;
import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;
import com.example.trippaminebe.domain.asset.repository.AssetGoalRepository;
import com.example.trippaminebe.domain.asset.repository.AssetSnapshotRepository;
import com.example.trippaminebe.domain.asset.repository.MonthlyBudgetRepository;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Set;
import java.util.stream.Collectors;


/**
 * =========================================================
 * 자산 Dashboard
 * =========================================================
 *
 * [수정 내역]
 *
 * 1. 자산 증감 계산 기준 변경
 *    - 예전: 시작자산 = 현재 잔액 − 이번 달 "모든" 수입 + 이번 달 "모든" 지출
 *      → 계좌를 지정하지 않은 가계부 내역은 잔액을 바꾸지 않는데도
 *        계산에 들어가서, 증감률이 실제와 다르게 나왔다.
 *    - 지금: 계좌 잔액에 "실제로 반영된" 거래(ACCOUNT_ID 가 연동 중인 계좌인 거래)만
 *      거꾸로 빼서 그 시점의 잔액을 구한다.
 *      잔액(T) = 현재 잔액 − T 이후 반영된 수입 + T 이후 반영된 지출
 *
 * 2. 과거 월 조회 시 "현재 잔액" 대신 "그 달 말일 잔액" 을 총자산으로 사용
 *
 * 3. Snapshot 저장을 AssetSnapshotWriter(별도 트랜잭션)로 분리
 *    → 동시 요청에도 같은 날짜 Snapshot 이 중복되지 않음
 *
 * 4. Dashboard 조회 자체는 readOnly 트랜잭션
 *
 * 수입/지출/카테고리 통계는 예전처럼 "가계부 전체 내역" 기준이다.
 * Snapshot 의 totalIncome / totalExpense 는 "그 달 1일부터 그날까지의 누적값" 이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetDashboardService {

    private final AssetSnapshotRepository assetSnapshotRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final AssetGoalRepository assetGoalRepository;
    private final AccountBookRepository accountBookRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AssetSnapshotWriter assetSnapshotWriter;


    // =====================================================
    // Dashboard
    // =====================================================

    public AssetDashboardResponseDto getDashboard(
            Long userId,
            Integer year,
            Integer month
    ) {

        requireExistingUser(userId);


        // 1. 조회 년월
        YearMonth targetYearMonth;

        try {
            targetYearMonth =
                    YearMonth.of(year, month);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "조회 년월이 올바르지 않습니다."
            );
        }

        LocalDateTime monthStart =
                targetYearMonth.atDay(1).atStartOfDay();

        LocalDateTime nextMonthStart =
                targetYearMonth.plusMonths(1).atDay(1).atStartOfDay();

        LocalDate today =
                LocalDate.now();

        boolean isCurrentMonth =
                targetYearMonth.equals(YearMonth.from(today));


        // 2. 그 달 거래 (수입/지출/카테고리 통계용 — 가계부 전체)
        List<TransactionEntity> monthTransactions =
                accountBookRepository
                        .findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                                userId,
                                monthStart,
                                nextMonthStart
                        );

        long totalIncome =
                sumByType(monthTransactions, "income");

        long totalExpense =
                sumByType(monthTransactions, "expense");

        Map<String, Long> categoryExpenseMap =
                buildCategoryExpenseMap(monthTransactions);


        // 3. 잔액 역산 준비
        BalanceCalculator balance =
                createBalanceCalculator(
                        userId,
                        monthStart
                );


        // 4. 총자산 / 시작자산
        //    - 현재 월: 지금 잔액 합계
        //    - 과거 월: 그 달 말일 잔액
        long totalAsset =
                isCurrentMonth
                        ? balance.currentTotal()
                        : balance.assetAt(nextMonthStart);

        long startAsset =
                balance.assetAt(monthStart);

        long assetChangeAmount =
                totalAsset - startAsset;

        double assetChangeRate =
                calculateAssetChangeRate(
                        startAsset,
                        totalAsset
                );


        // 5. 현재 월이면 오늘 Snapshot 저장 (별도 트랜잭션)
        if (isCurrentMonth) {
            saveTodaySnapshotSafely(
                    userId,
                    today,
                    totalAsset,
                    totalIncome,
                    totalExpense
            );
        }


        // 6. 그 달의 일별 흐름 (응답 호환용)
        List<AssetDashboardResponseDto.AssetHistoryInfo> assetHistory =
                buildMonthlyAssetHistory(
                        targetYearMonth,
                        today,
                        monthTransactions,
                        balance
                );


        // 7. 예산 / 목표
        List<MonthlyBudget> monthlyBudgets =
                monthlyBudgetRepository
                        .findByUserIdAndBudgetYearAndBudgetMonth(
                                userId,
                                year,
                                month
                        );

        List<AssetGoal> assetGoals =
                assetGoalRepository.findByUserId(userId);


        log.info(
                "[Asset Dashboard] userId={} {}-{} 총자산={} 시작자산={} 수입={} 지출={} 증감률={}%",
                userId, year, month,
                totalAsset, startAsset,
                totalIncome, totalExpense,
                assetChangeRate
        );


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
// 미래 자산 시뮬레이션
// =====================================================

    public AssetForecastResponseDto getAssetForecast(
            Long userId,
            Long goalId

    ) {

        // =================================================
        // 1. 로그인 사용자 확인
        // =================================================

        requireExistingUser(userId);


        LocalDate today =
                LocalDate.now();


        // =================================================
        // 2. 최근 3개월 거래 조회
        // =================================================
        //
        // 미래 예측에 한 달치 데이터만 사용하는 것보다
        // 최근 3개월 평균값을 사용하는 편이 안정적이다.
        // =================================================

        LocalDateTime startDate =
                today
                        .minusMonths(3)
                        .atStartOfDay();


        LocalDateTime endDate =
                today
                        .plusDays(1)
                        .atStartOfDay();


        List<TransactionEntity> recentTransactions =
                accountBookRepository
                        .findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                                userId,
                                startDate,
                                endDate
                        );


        // =================================================
        // 3. 최근 3개월 총수입 / 총지출
        // =================================================

        long totalIncome =
                sumByType(
                        recentTransactions,
                        "income"
                );


        long totalExpense =
                sumByType(
                        recentTransactions,
                        "expense"
                );


        // =================================================
        // 4. 월 평균 수입 / 지출
        // =================================================

        long averageMonthlyIncome =
                totalIncome / 3;


        long averageMonthlyExpense =
                totalExpense / 3;


        // =================================================
        // 5. 월 예상 저축 가능 금액
        // =================================================

        long monthlySaving =
                averageMonthlyIncome
                        - averageMonthlyExpense;


        // =================================================
        // 6. 현재 총자산
        // =================================================
        //
        // 기존 Dashboard에서 사용하고 있는
        // BalanceCalculator를 그대로 재사용한다.
        // =================================================

        BalanceCalculator balance =
                createBalanceCalculator(
                        userId,
                        startDate
                );


        long currentAsset =
                balance.currentTotal();


        // =================================================
        // 7. 미래 예상 자산
        // =================================================

        long after3Months =
                currentAsset
                        + (monthlySaving * 3);


        long after6Months =
                currentAsset
                        + (monthlySaving * 6);


        long after12Months =
                currentAsset
                        + (monthlySaving * 12);


        // =================================================
// 8. 선택한 여행 목표 조회
// =================================================

        AssetGoal goal = null;


// 사용자가 화면에서 여행 목표를 선택한 경우
        if (goalId != null) {

            goal =
                    assetGoalRepository
                            .findByGoalIdAndUserId(
                                    goalId,
                                    userId
                            )
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "선택한 여행 목표를 찾을 수 없습니다."
                                            )
                            );


            // 여행 목표인지 확인
            if (
                    goal.getGoalType() == null
                            ||
                            !"TRAVEL".equalsIgnoreCase(
                                    goal.getGoalType()
                            )
            ) {

                throw new IllegalArgumentException(
                        "여행 목표만 분석할 수 있습니다."
                );
            }


            // 진행 중인 목표인지 확인
            if (
                    goal.getStatus() != null
                            &&
                            !"ACTIVE".equalsIgnoreCase(
                                    goal.getStatus()
                            )
            ) {

                throw new IllegalArgumentException(
                        "진행 중인 여행 목표만 분석할 수 있습니다."
                );
            }

        }


// =================================================
// 9. 선택한 목표가 없으면 기본 여행 목표 조회
// =================================================

        else {

            List<AssetGoal> travelGoals =
                    assetGoalRepository
                            .findByUserIdAndGoalTypeAndStatus(
                                    userId,
                                    "TRAVEL",
                                    "ACTIVE"
                            );


            // 여행 목표 자체가 없는 경우
            if (travelGoals.isEmpty()) {

                String message;


                if (monthlySaving > 0) {

                    message =
                            "현재 소비 패턴을 유지하면 매달 약 "
                                    + String.format("%,d", monthlySaving)
                                    + "원을 저축할 수 있습니다. "
                                    + "여행 목표를 등록하면 예상 달성 시기를 계산해드립니다.";

                } else {

                    message =
                            "현재 평균 지출이 수입보다 높거나 비슷합니다. "
                                    + "지출을 줄인 뒤 여행 목표를 등록해보세요.";
                }


                return new AssetForecastResponseDto(

                        currentAsset,

                        averageMonthlyIncome,

                        averageMonthlyExpense,

                        monthlySaving,

                        after3Months,

                        after6Months,

                        after12Months,

                        null,

                        null,

                        null,

                        null,

                        null,

                        null,

                        null,

                        message
                );
            }


            // 아무것도 선택하지 않았을 경우 첫 여행을 기본 선택
            goal =
                    travelGoals.get(0);
        }
        long targetAmount =
                goal.getTargetAmount() != null
                        ? goal.getTargetAmount()
                        : 0L;


        long currentGoalAmount =
                goal.getCurrentAmount() != null
                        ? goal.getCurrentAmount()
                        : 0L;


        // =================================================
        // 11. 목표까지 남은 금액
        // =================================================

        long remainingAmount =
                Math.max(
                        0L,
                        targetAmount - currentGoalAmount
                );


        // =================================================
        // 12. 목표 달성 예상 개월 수
        // =================================================

        Integer monthsToGoal = null;

        LocalDate expectedGoalDate = null;


        if (remainingAmount == 0) {

            monthsToGoal = 0;

            expectedGoalDate =
                    today;

        } else if (monthlySaving > 0) {

            monthsToGoal =
                    (int) Math.ceil(
                            (double) remainingAmount
                                    /
                                    monthlySaving
                    );


            expectedGoalDate =
                    today.plusMonths(
                            monthsToGoal
                    );
        }


        // =================================================
        // 13. 사용자에게 보여줄 분석 메시지
        // =================================================

        String forecastMessage;


        if (remainingAmount == 0) {

            forecastMessage =
                    goal.getGoalName()
                            + " 목표를 이미 달성했습니다!";


        } else if (monthlySaving <= 0) {

            forecastMessage =
                    goal.getGoalName()
                            + " 목표를 달성하려면 현재보다 지출을 줄여 "
                            + "월 저축 가능 금액을 먼저 확보하는 것이 좋습니다.";


        } else if (
                monthsToGoal != null
                        &&
                        monthsToGoal > 1
        ) {

            // =============================================
            // 목표를 한 달 앞당기려면 필요한 월 저축액
            // =============================================

            long fasterMonthlySaving =
                    (long) Math.ceil(
                            (double) remainingAmount
                                    /
                                    (monthsToGoal - 1)
                    );


            long additionalSaving =
                    Math.max(
                            0L,
                            fasterMonthlySaving
                                    - monthlySaving
                    );


            forecastMessage =
                    "현재 소비 패턴을 유지하면 약 "
                            + monthsToGoal
                            + "개월 후 "
                            + goal.getGoalName()
                            + " 목표를 달성할 수 있습니다. "
                            + "목표를 약 1개월 앞당기고 싶다면 "
                            + "매달 약 "
                            + String.format(
                            "%,d",
                            additionalSaving
                    )
                            + "원을 추가로 절약해보세요.";


        } else {

            forecastMessage =
                    "현재 소비 패턴을 유지하면 "
                            + goal.getGoalName()
                            + " 목표를 약 1개월 안에 달성할 수 있습니다.";
        }

        // =================================================
        // 14. 결과 반환
        // =================================================

        return new AssetForecastResponseDto(

                currentAsset,

                averageMonthlyIncome,

                averageMonthlyExpense,

                monthlySaving,

                after3Months,

                after6Months,

                after12Months,

                goal.getGoalId(),

                goal.getGoalName(),

                targetAmount,

                currentGoalAmount,

                remainingAmount,

                monthsToGoal,

                expectedGoalDate,

                forecastMessage
        );
    }

    // =====================================================
    // 오늘 Snapshot 갱신 (스케줄러용)
    // =====================================================
    //
    // AssetSnapshotScheduler 가 매일 밤 호출한다.
    // 사용자가 Dashboard 를 열지 않은 날에도 자산 흐름 그래프에 점이 생긴다.
    //
    // 연동 계좌도 없고 이번 달 가계부 기록도 없으면 저장하지 않는다.
    // =====================================================

    public boolean refreshTodaySnapshot(
            Long userId
    ) {

        requireExistingUser(userId);

        LocalDate today =
                LocalDate.now();

        YearMonth currentMonth =
                YearMonth.from(today);

        LocalDateTime monthStart =
                currentMonth.atDay(1).atStartOfDay();

        LocalDateTime nextMonthStart =
                currentMonth.plusMonths(1).atDay(1).atStartOfDay();

        List<TransactionEntity> monthTransactions =
                accountBookRepository
                        .findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                                userId,
                                monthStart,
                                nextMonthStart
                        );

        List<Account> activeAccounts =
                accountRepository.findByUser_IdAndLinkStatus(
                        userId,
                        LinkStatus.ACTIVE
                );

        if (activeAccounts.isEmpty() && monthTransactions.isEmpty()) {
            return false;
        }

        long totalAsset =
                activeAccounts.stream()
                        .map(Account::getBalance)
                        .filter(value -> value != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .longValue();

        saveTodaySnapshotSafely(
                userId,
                today,
                totalAsset,
                sumByType(monthTransactions, "income"),
                sumByType(monthTransactions, "expense")
        );

        return true;
    }


    // =====================================================
    // 기간별 자산 흐름
    // =====================================================
    //
    // WEEK    : 최근 7일
    // MONTH   : 최근 1개월
    // QUARTER : 최근 3개월
    // YEAR    : 최근 1년
    //
    // Snapshot 개수에 의존하지 않고 실제 거래내역과
    // 현재 연동계좌 잔액을 기준으로 과거 자산을 역산한다.
    // =====================================================

    public AssetHistoryResponseDto getAssetHistoryByPeriod(
            Long userId,
            String period
    ) {

        // -------------------------------------------------
        // 1. 사용자
        // -------------------------------------------------

        requireExistingUser(userId);


        // -------------------------------------------------
        // 2. 기간 확인
        // -------------------------------------------------

        String normalizedPeriod =
                period == null || period.isBlank()
                        ? "MONTH"
                        : period.trim().toUpperCase();


        LocalDate endDate =
                LocalDate.now();


        LocalDate startDate =
                switch (normalizedPeriod) {

                    case "WEEK" ->
                            endDate.minusDays(6);

                    case "MONTH" ->
                            endDate.minusMonths(1).plusDays(1);

                    case "QUARTER" ->
                            endDate.minusMonths(3).plusDays(1);

                    case "YEAR" ->
                            endDate.minusYears(1).plusDays(1);

                    default ->
                            throw new IllegalArgumentException(
                                    "지원하지 않는 조회 기간입니다. "
                                            + "WEEK, MONTH, QUARTER, YEAR 중 하나를 사용해주세요."
                            );
                };


        LocalDateTime startDateTime =
                startDate.atStartOfDay();


        LocalDateTime endExclusive =
                endDate
                        .plusDays(1)
                        .atStartOfDay();


        // -------------------------------------------------
        // 3. 기간 내 전체 가계부 거래
        // -------------------------------------------------

        List<TransactionEntity> transactions =
                accountBookRepository
                        .findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                                userId,
                                startDateTime,
                                endExclusive
                        );


        long totalIncome =
                sumByType(
                        transactions,
                        "income"
                );


        long totalExpense =
                sumByType(
                        transactions,
                        "expense"
                );


        long netCashFlow =
                totalIncome - totalExpense;


        // -------------------------------------------------
        // 4. 계좌 잔액 역산기
        // -------------------------------------------------

        BalanceCalculator balance =
                createBalanceCalculator(
                        userId,
                        startDateTime
                );


        long startAsset =
                balance.assetAt(
                        startDateTime
                );


        long currentAsset =
                balance.currentTotal();


        long changeAmount =
                currentAsset - startAsset;


        double changeRate =
                calculateAssetChangeRate(
                        startAsset,
                        currentAsset
                );


        // -------------------------------------------------
        // 5. 날짜별 거래 Map
        // -------------------------------------------------

        Map<LocalDate, Long> dailyIncome =
                new LinkedHashMap<>();


        Map<LocalDate, Long> dailyExpense =
                new LinkedHashMap<>();


        for (TransactionEntity transaction : transactions) {

            if (transaction.getTransactionDate() == null) {
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


            if ("income".equalsIgnoreCase(transaction.getType())) {

                dailyIncome.merge(
                        transactionDate,
                        amount,
                        Long::sum
                );

            } else if ("expense".equalsIgnoreCase(transaction.getType())) {

                dailyExpense.merge(
                        transactionDate,
                        amount,
                        Long::sum
                );
            }
        }


        // -------------------------------------------------
        // 6. 그래프 데이터
        // -------------------------------------------------

        List<AssetHistoryResponseDto.AssetPoint> assetHistory =
                new ArrayList<>();


        List<AssetHistoryResponseDto.CashFlowPoint> cashFlow =
                new ArrayList<>();


        LocalDate cursor =
                startDate;


        while (!cursor.isAfter(endDate)) {

            LocalDateTime nextDay =
                    cursor
                            .plusDays(1)
                            .atStartOfDay();


            long assetAmount =
                    balance.assetAt(nextDay);


            assetHistory.add(
                    AssetHistoryResponseDto.AssetPoint
                            .builder()
                            .date(cursor)
                            .amount(assetAmount)
                            .build()
            );


            cashFlow.add(
                    AssetHistoryResponseDto.CashFlowPoint
                            .builder()
                            .date(cursor)
                            .income(
                                    dailyIncome.getOrDefault(
                                            cursor,
                                            0L
                                    )
                            )
                            .expense(
                                    dailyExpense.getOrDefault(
                                            cursor,
                                            0L
                                    )
                            )
                            .build()
            );


            cursor =
                    nextGraphDate(
                            cursor,
                            normalizedPeriod
                    );
        }


        // 마지막 날짜가 그래프에 포함되지 않았다면 추가
        if (
                assetHistory.isEmpty()
                        || !assetHistory
                        .get(assetHistory.size() - 1)
                        .getDate()
                        .equals(endDate)
        ) {

            assetHistory.add(
                    AssetHistoryResponseDto.AssetPoint
                            .builder()
                            .date(endDate)
                            .amount(
                                    balance.assetAt(
                                            endExclusive
                                    )
                            )
                            .build()
            );


            cashFlow.add(
                    AssetHistoryResponseDto.CashFlowPoint
                            .builder()
                            .date(endDate)
                            .income(
                                    dailyIncome.getOrDefault(
                                            endDate,
                                            0L
                                    )
                            )
                            .expense(
                                    dailyExpense.getOrDefault(
                                            endDate,
                                            0L
                                    )
                            )
                            .build()
            );
        }


        // -------------------------------------------------
        // 7. 로그
        // -------------------------------------------------

        log.info(
                "[Asset History] userId={} period={} start={} end={} startAsset={} currentAsset={} income={} expense={}",
                userId,
                normalizedPeriod,
                startDate,
                endDate,
                startAsset,
                currentAsset,
                totalIncome,
                totalExpense
        );


        // -------------------------------------------------
        // 8. 응답
        // -------------------------------------------------

        return AssetHistoryResponseDto
                .builder()
                .period(normalizedPeriod)
                .startDate(startDate)
                .endDate(endDate)
                .startAsset(startAsset)
                .currentAsset(currentAsset)
                .changeAmount(changeAmount)
                .changeRate(changeRate)
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .netCashFlow(netCashFlow)
                .assetHistory(assetHistory)
                .cashFlow(cashFlow)
                .build();
    }
    // =====================================================
    // 그래프 날짜 간격
    // =====================================================

    private LocalDate nextGraphDate(
            LocalDate currentDate,
            String period
    ) {

        return switch (period) {

            // 일 단위
            case "WEEK", "MONTH" ->
                    currentDate.plusDays(1);

            // 7일 단위
            case "QUARTER" ->
                    currentDate.plusDays(7);

            // 월 단위
            case "YEAR" ->
                    currentDate.plusMonths(1);

            default ->
                    currentDate.plusDays(1);
        };
    }

    // =====================================================
    // 내부 — Snapshot 저장 (중복/동시성 방어)
    // =====================================================

    private void saveTodaySnapshotSafely(
            Long userId,
            LocalDate today,
            long totalAsset,
            long totalIncome,
            long totalExpense
    ) {
        try {
            assetSnapshotWriter.upsert(
                    userId, today, totalAsset, totalIncome, totalExpense
            );
        } catch (DataIntegrityViolationException firstTry) {
            // 동시에 들어온 다른 요청이 먼저 INSERT 한 경우 → 이번엔 UPDATE 로 처리된다
            try {
                assetSnapshotWriter.upsert(
                        userId, today, totalAsset, totalIncome, totalExpense
                );
            } catch (RuntimeException retry) {
                // Snapshot 저장 실패가 Dashboard 조회 자체를 깨뜨리지 않도록 로그만 남긴다
                log.warn("[AssetSnapshot] userId={} 저장 재시도 실패: {}", userId, retry.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("[AssetSnapshot] userId={} 저장 실패: {}", userId, e.getMessage());
        }
    }


    // =====================================================
    // 내부 — 그 달 일별 흐름 (응답 호환용)
    // =====================================================

    private List<AssetDashboardResponseDto.AssetHistoryInfo>
    buildMonthlyAssetHistory(
            YearMonth targetYearMonth,
            LocalDate today,
            List<TransactionEntity> monthTransactions,
            BalanceCalculator balance
    ) {

        List<AssetDashboardResponseDto.AssetHistoryInfo> history =
                new ArrayList<>();

        YearMonth currentYearMonth =
                YearMonth.from(today);

        if (targetYearMonth.isAfter(currentYearMonth)) {
            return history;
        }

        LocalDate monthStart =
                targetYearMonth.atDay(1);

        LocalDate monthEnd =
                targetYearMonth.atEndOfMonth();

        LocalDate graphEndDate =
                targetYearMonth.equals(currentYearMonth) && today.isBefore(monthEnd)
                        ? today
                        : monthEnd;


        // 날짜별 수입 / 지출 (가계부 전체)
        Map<LocalDate, Long> dailyIncome = new LinkedHashMap<>();
        Map<LocalDate, Long> dailyExpense = new LinkedHashMap<>();

        for (TransactionEntity transaction : monthTransactions) {

            if (transaction.getTransactionDate() == null) {
                continue;
            }

            LocalDate date =
                    transaction.getTransactionDate().toLocalDate();

            long amount =
                    safeAmount(transaction.getAmount());

            if ("income".equalsIgnoreCase(transaction.getType())) {
                dailyIncome.merge(date, amount, Long::sum);
            } else if ("expense".equalsIgnoreCase(transaction.getType())) {
                dailyExpense.merge(date, amount, Long::sum);
            }
        }


        long cumulativeIncome = 0L;
        long cumulativeExpense = 0L;

        for (
                LocalDate date = monthStart;
                !date.isAfter(graphEndDate);
                date = date.plusDays(1)
        ) {

            cumulativeIncome += dailyIncome.getOrDefault(date, 0L);
            cumulativeExpense += dailyExpense.getOrDefault(date, 0L);

            history.add(
                    new AssetDashboardResponseDto.AssetHistoryInfo(
                            date,
                            // 그날 하루가 끝난 시점의 계좌 잔액 합계
                            balance.assetAt(date.plusDays(1).atStartOfDay()),
                            cumulativeIncome,
                            cumulativeExpense
                    )
            );
        }

        return history;
    }


    // =====================================================
    // 내부 — 잔액 역산기
    // =====================================================

    private BalanceCalculator createBalanceCalculator(
            Long userId,
            LocalDateTime from
    ) {

        List<Account> activeAccounts =
                accountRepository.findByUser_IdAndLinkStatus(
                        userId,
                        LinkStatus.ACTIVE
                );

        long currentTotal =
                activeAccounts.stream()
                        .map(Account::getBalance)
                        .filter(value -> value != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .longValue();

        Set<Long> activeAccountIds =
                activeAccounts.stream()
                        .map(Account::getAccountId)
                        .collect(Collectors.toSet());

        // from 이후 거래 중 "연동 중인 계좌에 반영된" 것만
        List<TransactionEntity> linkedSince =
                accountBookRepository
                        .findByUserIdAndTransactionDateGreaterThanEqual(
                                userId,
                                from
                        )
                        .stream()
                        .filter(transaction ->
                                transaction.getAccountId() != null
                                        && activeAccountIds.contains(transaction.getAccountId())
                                        && transaction.getTransactionDate() != null
                        )
                        .toList();

        return new BalanceCalculator(currentTotal, linkedSince);
    }


    /**
     * 현재 잔액에서 "T 이후에 계좌에 반영된 거래"를 되돌려 T 시점 잔액을 구한다.
     * (가계부에서 계좌를 지정해 입력한 거래만 잔액을 바꾸기 때문 — AccountBalanceService 참고)
     */
    private record BalanceCalculator(
            long currentTotal,
            List<TransactionEntity> linkedTransactions
    ) {

        long assetAt(LocalDateTime moment) {

            long asset = currentTotal;

            for (TransactionEntity transaction : linkedTransactions) {

                if (transaction.getTransactionDate().isBefore(moment)) {
                    continue;
                }

                long amount =
                        transaction.getAmount() != null
                                ? transaction.getAmount()
                                : 0L;

                if ("income".equalsIgnoreCase(transaction.getType())) {
                    asset -= amount;
                } else if ("expense".equalsIgnoreCase(transaction.getType())) {
                    asset += amount;
                }
            }

            return asset;
        }
    }


    // =====================================================
    // 내부 — 공통 계산
    // =====================================================

    private void requireExistingUser(
            Long userId
    ) {
        userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다. userId=" + userId
                        )
                );
    }


    private long sumByType(
            List<TransactionEntity> transactions,
            String type
    ) {
        return transactions.stream()
                .filter(transaction -> type.equalsIgnoreCase(transaction.getType()))
                .mapToLong(transaction -> safeAmount(transaction.getAmount()))
                .sum();
    }


    private Map<String, Long> buildCategoryExpenseMap(
            List<TransactionEntity> transactions
    ) {

        Map<String, Long> categoryExpenseMap =
                new LinkedHashMap<>();

        transactions.stream()
                .filter(transaction -> "expense".equalsIgnoreCase(transaction.getType()))
                .forEach(transaction -> {

                    String category =
                            transaction.getCategory();

                    if (category == null || category.isBlank()) {
                        category = "ETC";
                    }

                    categoryExpenseMap.merge(
                            category.toUpperCase(),
                            safeAmount(transaction.getAmount()),
                            Long::sum
                    );
                });

        return categoryExpenseMap;
    }


    /**
     * 자산 증감률 (소수점 첫째 자리).
     * 시작 자산이 0 이하이면 비율이 의미가 없으므로 0 을 반환한다.
     * (예전에는 음수 시작자산일 때 부호가 뒤집힌 비율이 나왔다)
     */
    private double calculateAssetChangeRate(
            long startAsset,
            long currentAsset
    ) {
        if (startAsset <= 0L) {
            return 0.0;
        }

        double changeRate =
                ((double) (currentAsset - startAsset) / startAsset) * 100.0;

        return Math.round(changeRate * 10.0) / 10.0;
    }


    private long safeAmount(
            Long amount
    ) {
        return amount != null ? amount : 0L;
    }


    private long safeId(
            AssetSnapshot snapshot
    ) {
        return snapshot.getSnapshotId() != null ? snapshot.getSnapshotId() : 0L;
    }
}
