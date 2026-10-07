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


    public AssetDashboardResponseDto getDashboard(
            Long userId,
            Integer year,
            Integer month
    ) {

        // =========================================
        // 1. 사용자 조회
        // =========================================

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "사용자를 찾을 수 없습니다. userId=" + userId
                                )
                        );


        String username =
                user.getUserName();


        // =========================================
        // 2. 조회할 월 시작/종료
        // =========================================

        LocalDateTime startDate =
                LocalDateTime.of(
                        year,
                        month,
                        1,
                        0,
                        0
                );


        LocalDateTime endDate =
                startDate.plusMonths(1);


        // =========================================
        // 3. 해당 월 가계부 거래 조회
        // =========================================

        List<TransactionEntity> transactions =
                accountBookRepository
                        .findByUsernameAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                                username,
                                startDate,
                                endDate
                        );


        // =========================================
        // 4. 월 총 수입
        // =========================================

        Long totalIncome =
                transactions.stream()

                        .filter(
                                transaction ->
                                        "income".equalsIgnoreCase(
                                                transaction.getType()
                                        )
                        )

                        .mapToLong(
                                transaction ->
                                        transaction.getAmount() != null
                                                ? transaction.getAmount()
                                                : 0L
                        )

                        .sum();


        // =========================================
        // 5. 월 총 지출
        // =========================================

        Long totalExpense =
                transactions.stream()

                        .filter(
                                transaction ->
                                        "expense".equalsIgnoreCase(
                                                transaction.getType()
                                        )
                        )

                        .mapToLong(
                                transaction ->
                                        transaction.getAmount() != null
                                                ? transaction.getAmount()
                                                : 0L
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


                    Long amount =
                            transaction.getAmount() != null
                                    ? transaction.getAmount()
                                    : 0L;


                    categoryExpenseMap.merge(
                            category,
                            amount,
                            Long::sum
                    );
                });


        // =========================================
        // 7. ACTIVE 계좌 조회
        // =========================================

        List<Account> activeAccounts =
                accountRepository
                        .findByUser_IdAndLinkStatus(
                                userId,
                                LinkStatus.ACTIVE
                        );


        // =========================================
        // 8. ACTIVE 계좌의 잔액을 모두 합산
        // =========================================

        BigDecimal totalAssetDecimal =
                activeAccounts.stream()

                        .map(Account::getBalance)

                        .filter(
                                balance ->
                                        balance != null
                        )

                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        Long totalAsset =
                totalAssetDecimal.longValue();


        // =========================================
        // 9. 오늘 날짜
        // =========================================

        LocalDate today =
                LocalDate.now();


        // =========================================
        // 10. 오늘 Snapshot 조회
        //
        // 있으면 UPDATE
        // 없으면 INSERT
        // =========================================

        AssetSnapshot todaySnapshot =
                assetSnapshotRepository
                        .findByUserIdAndSnapshotDate(
                                userId,
                                today
                        )
                        .orElse(null);


        if (todaySnapshot == null) {

            // =====================================
            // 오늘 Snapshot이 없으면 새로 생성
            // =====================================

            todaySnapshot =
                    AssetSnapshot.builder()

                            .userId(userId)

                            .snapshotDate(today)

                            .totalAsset(totalAsset)

                            .totalIncome(totalIncome)

                            .totalExpense(totalExpense)

                            // 아직 여행지출 자동계산 전이므로 0
                            .travelExpense(0L)

                            .build();


            assetSnapshotRepository.save(
                    todaySnapshot
            );


        } else {

            // =====================================
            // 오늘 Snapshot이 있으면 최신 값으로 갱신
            // =====================================

            todaySnapshot.setTotalAsset(
                    totalAsset
            );

            todaySnapshot.setTotalIncome(
                    totalIncome
            );

            todaySnapshot.setTotalExpense(
                    totalExpense
            );


            // JPA Dirty Checking으로 UPDATE 되지만
            // 명확성을 위해 save 호출
            assetSnapshotRepository.save(
                    todaySnapshot
            );
        }


        // =========================================
        // 11. 최신 Snapshot 다시 조회
        // =========================================

        AssetSnapshot latestSnapshot =
                assetSnapshotRepository
                        .findTopByUserIdOrderBySnapshotDateDesc(
                                userId
                        )
                        .orElse(null);


        // =========================================
        // 12. 자산 변화 그래프
        //
        // 방금 저장한 오늘 Snapshot도 포함된다.
        // =========================================

        List<AssetSnapshot> snapshots =
                assetSnapshotRepository
                        .findByUserIdOrderBySnapshotDateAsc(
                                userId
                        );


        // =========================================
        // 13. 월별 예산
        // =========================================

        List<MonthlyBudget> monthlyBudgets =
                monthlyBudgetRepository
                        .findByUserIdAndBudgetYearAndBudgetMonth(
                                userId,
                                year,
                                month
                        );


        // =========================================
        // 14. 자산 목표
        // =========================================

        List<AssetGoal> assetGoals =
                assetGoalRepository
                        .findByUserId(
                                userId
                        );


        // =========================================
        // 개발 확인용 로그
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
                "username = " + username
        );

        System.out.println(
                "조회기간 = "
                        + startDate
                        + " ~ "
                        + endDate
        );

        System.out.println(
                "거래건수 = "
                        + transactions.size()
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
                "카테고리별 지출 = "
                        + categoryExpenseMap
        );

        System.out.println(
                "ACTIVE 계좌 수 = "
                        + activeAccounts.size()
        );

        System.out.println(
                "현재 총 자산 = "
                        + totalAsset
        );

        System.out.println(
                "오늘 Snapshot 날짜 = "
                        + today
        );

        System.out.println(
                "Snapshot 총 자산 = "
                        + todaySnapshot.getTotalAsset()
        );

        System.out.println(
                "Snapshot 개수 = "
                        + snapshots.size()
        );


        activeAccounts.forEach(account -> {

            System.out.println(
                    "계좌 = "
                            + account.getBankName()
                            + " / "
                            + account.getAccountAlias()
                            + " / 잔액 = "
                            + account.getBalance()
            );
        });


        System.out.println(
                "================================="
        );


        // =========================================
        // 15. Dashboard DTO 반환
        // =========================================

        return AssetDashboardResponseDto.of(

                userId,

                year,

                month,

                totalAsset,

                latestSnapshot,

                snapshots,

                monthlyBudgets,

                assetGoals,

                totalIncome,

                totalExpense,

                categoryExpenseMap
        );
    }
}