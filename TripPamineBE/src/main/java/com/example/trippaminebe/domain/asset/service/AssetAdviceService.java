package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.asset.dto.AssetAdviceResponseDto;
import com.example.trippaminebe.domain.asset.dto.TravelGoalRequestDto;
import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.repository.AssetGoalRepository;
import com.example.trippaminebe.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetAdviceService {

    private final AssetGoalRepository assetGoalRepository;
    private final AccountBookRepository accountBookRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createTravelGoal(
            Long userId,
            TravelGoalRequestDto request
    ) {

        // =========================================
        // 1. 로그인 사용자 존재 확인
        // =========================================
        userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다. userId=" + userId
                        )
                );


        // =========================================
        // 2. 여행 목표 Entity 생성
        // =========================================
        AssetGoal assetGoal = AssetGoal.builder()
                .userId(userId)
                .goalName(request.getGoalName())
                .targetAmount(request.getTargetAmount())
                .currentAmount(request.getCurrentAmount())
                .targetDate(request.getTargetDate())
                .goalType("TRAVEL")
                .status("ACTIVE")
                .build();


        // =========================================
        // 3. Oracle DB 저장
        // =========================================
        AssetGoal savedGoal =
                assetGoalRepository.save(assetGoal);


        // =========================================
        // 4. 생성된 목표 ID 반환
        // =========================================
        return savedGoal.getGoalId();
    }


    // =====================================================
// 여행 목표 수정
// =====================================================

    @Transactional
    public void updateTravelGoal(
            Long userId,
            Long goalId,
            TravelGoalRequestDto request
    ) {

        AssetGoal goal =
                assetGoalRepository
                        .findByGoalIdAndUserId(
                                goalId,
                                userId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "수정할 여행 목표를 찾을 수 없습니다."
                                )
                        );


        if (
                goal.getGoalType() == null
                        ||
                        !"TRAVEL".equalsIgnoreCase(
                                goal.getGoalType()
                        )
        ) {

            throw new IllegalArgumentException(
                    "여행 목표만 수정할 수 있습니다."
            );
        }


        goal.setGoalName(
                request.getGoalName()
        );

        goal.setTargetAmount(
                request.getTargetAmount()
        );

        goal.setCurrentAmount(
                request.getCurrentAmount()
        );

        goal.setTargetDate(
                request.getTargetDate()
        );
    }
    // =========================================================
    // 여행자금 자산관리 가이드
    // =========================================================
    public AssetAdviceResponseDto getAssetAdvice(Long userId) {

        // =====================================================
        // 1. 사용자 조회
        // =====================================================
        userRepository.findById(userId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "사용자를 찾을 수 없습니다. userId=" + userId
                        )
                );


        // =====================================================
        // 2. 최근 3개월 거래내역 조회
        // =====================================================
        LocalDate today = LocalDate.now();

        LocalDateTime startDate =
                today.minusMonths(3)
                        .atStartOfDay();

        LocalDateTime endDate =
                today.plusDays(1)
                        .atStartOfDay();


        List<TransactionEntity> transactions =
                accountBookRepository
                        .findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                                userId,
                                startDate,
                                endDate
                        );


        // =====================================================
        // 3. 최근 3개월 총수입 / 총지출 계산
        // =====================================================
        long totalIncome = 0L;
        long totalExpense = 0L;

        for (TransactionEntity transaction : transactions) {

            long amount =
                    transaction.getAmount() == null
                            ? 0L
                            : transaction.getAmount();


            String type =
                    transaction.getType();


            if ("income".equalsIgnoreCase(type)) {

                totalIncome += amount;

            } else if ("expense".equalsIgnoreCase(type)) {

                totalExpense += amount;
            }
        }


        // =====================================================
        // 4. 월평균 수입 / 지출 계산
        //
        // 최근 3개월 데이터를 사용하므로 3으로 나눈다.
        // =====================================================
        long monthlyAverageIncome =
                Math.round(
                        totalIncome / 3.0
                );

        long monthlyAverageExpense =
                Math.round(
                        totalExpense / 3.0
                );


        // =====================================================
        // 5. 월평균 여유자금
        // =====================================================
        long monthlySurplus =
                monthlyAverageIncome
                        - monthlyAverageExpense;


        // =====================================================
        // 6. 진행 중인 여행 목표 조회
        // =====================================================
        List<AssetGoal> travelGoals =
                assetGoalRepository
                        .findByUserIdAndGoalTypeAndStatus(
                                userId,
                                "TRAVEL",
                                "ACTIVE"
                        );


        // =====================================================
        // 7. 여행 목표가 없는 경우
        // =====================================================
        if (travelGoals.isEmpty()) {

            List<AssetAdviceResponseDto.RecommendationInfo>
                    recommendations =
                    new ArrayList<>();


            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("GOAL")

                            .title("여행 목표를 등록해보세요")

                            .description(
                                    "여행 목표 금액과 목표 날짜를 등록하면 " +
                                            "현재 수입과 지출을 분석해 필요한 저축액을 계산해드립니다."
                            )

                            .priority("HIGH")

                            .build()
            );


            return AssetAdviceResponseDto.builder()

                    .monthlyAverageIncome(
                            monthlyAverageIncome
                    )

                    .monthlyAverageExpense(
                            monthlyAverageExpense
                    )

                    .monthlySurplus(
                            monthlySurplus
                    )

                    .travelGoal(null)

                    .financialStatus(
                            "NO_GOAL"
                    )

                    .recommendations(
                            recommendations
                    )

                    .build();
        }


        // =====================================================
        // 8. 첫 번째 ACTIVE 여행 목표 사용
        // =====================================================
        AssetGoal goal =
                travelGoals.get(0);


        long targetAmount =
                safeLong(
                        goal.getTargetAmount()
                );


        long currentAmount =
                safeLong(
                        goal.getCurrentAmount()
                );


        // =====================================================
        // 9. 남은 여행자금
        // =====================================================
        long remainingAmount =
                Math.max(
                        targetAmount
                                - currentAmount,
                        0L
                );


        // =====================================================
        // 10. 목표 달성률
        // =====================================================
        double progressRate =
                calculateProgressRate(
                        currentAmount,
                        targetAmount
                );


        // =====================================================
        // 11. 목표일까지 남은 개월
        // =====================================================
        long remainingMonths =
                calculateRemainingMonths(
                        goal.getTargetDate()
                );


        // =====================================================
        // 12. 필요한 월 저축액
        // =====================================================
        long requiredMonthlySaving =
                calculateRequiredMonthlySaving(
                        remainingAmount,
                        remainingMonths
                );


        // =====================================================
        // 13. 여행 목표 응답 DTO
        // =====================================================
        AssetAdviceResponseDto.TravelGoalInfo
                travelGoalInfo =

                AssetAdviceResponseDto.TravelGoalInfo
                        .builder()

                        .goalId(
                                goal.getGoalId()
                        )

                        .goalName(
                                goal.getGoalName()
                        )

                        .targetAmount(
                                targetAmount
                        )

                        .currentAmount(
                                currentAmount
                        )

                        .remainingAmount(
                                remainingAmount
                        )

                        .progressRate(
                                progressRate
                        )

                        .targetDate(
                                goal.getTargetDate()
                        )

                        .remainingMonths(
                                remainingMonths
                        )

                        .requiredMonthlySaving(
                                requiredMonthlySaving
                        )

                        .build();


        // =====================================================
        // 14. 사용자 맞춤 추천 생성
        // =====================================================
        List<AssetAdviceResponseDto.RecommendationInfo>
                recommendations =
                new ArrayList<>();


        String financialStatus;


        // =====================================================
        // CASE 1
        // 이미 목표 달성
        // =====================================================
        if (remainingAmount <= 0) {

            financialStatus =
                    "COMPLETED";


            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("GOAL")

                            .title("여행자금 목표 달성")

                            .description(
                                    "설정한 여행자금 목표를 달성했습니다."
                            )

                            .priority("LOW")

                            .build()
            );
        }


        // =====================================================
        // CASE 2
        // 목표 날짜가 이미 지남
        // =====================================================
        else if (
                goal.getTargetDate() != null
                        &&
                        goal.getTargetDate()
                                .isBefore(today)
        ) {

            financialStatus =
                    "SHORTAGE";


            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("GOAL")

                            .title("여행 목표 날짜를 확인해주세요")

                            .description(
                                    "여행 목표 날짜가 지났지만 아직 " +
                                            String.format(
                                                    "%,d",
                                                    remainingAmount
                                            ) +
                                            "원이 부족합니다."
                            )

                            .priority("HIGH")

                            .build()
            );
        }


        // =====================================================
        // CASE 3
        // 월 여유자금이 필요한 저축액보다 충분
        // =====================================================
        else if (
                monthlySurplus
                        >= requiredMonthlySaving
                        &&
                        requiredMonthlySaving > 0
        ) {

            financialStatus =
                    "ACHIEVABLE";


            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("SAVING")

                            .title("현재 현금흐름으로 목표 달성 가능")

                            .description(
                                    "목표일까지 매월 약 " +
                                            String.format(
                                                    "%,d",
                                                    requiredMonthlySaving
                                            ) +
                                            "원을 저축하면 여행자금을 마련할 수 있습니다."
                            )

                            .priority("HIGH")

                            .build()
            );


            long remainingSurplus =
                    monthlySurplus
                            - requiredMonthlySaving;


            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("BUDGET")

                            .title("여행저축 후 예상 여유자금")

                            .description(
                                    "여행자금을 저축한 뒤에도 월평균 약 " +
                                            String.format(
                                                    "%,d",
                                                    Math.max(
                                                            remainingSurplus,
                                                            0L
                                                    )
                                            ) +
                                            "원의 여유자금이 남을 것으로 예상됩니다."
                            )

                            .priority("MEDIUM")

                            .build()
            );
        }


        // =====================================================
        // CASE 4
        // 거의 가능하지만 조금 부족
        //
        // 필요한 저축액의 70% 이상 확보 가능
        // =====================================================
        else if (
                requiredMonthlySaving > 0
                        &&
                        monthlySurplus > 0
                        &&
                        monthlySurplus
                                >= requiredMonthlySaving * 0.7
        ) {

            financialStatus =
                    "TIGHT";


            long shortage =
                    Math.max(
                            requiredMonthlySaving
                                    - monthlySurplus,
                            0L
                    );


            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("EXPENSE")

                            .title("조금만 지출을 줄이면 목표 달성이 가능합니다")

                            .description(
                                    "현재 월 여유자금 기준으로 약 " +
                                            String.format(
                                                    "%,d",
                                                    shortage
                                            ) +
                                            "원이 추가로 필요합니다. " +
                                            "소비 항목을 점검해보세요."
                            )

                            .priority("HIGH")

                            .build()
            );
        }


        // =====================================================
        // CASE 5
        // 현재 현금흐름으로 부족
        // =====================================================
        else {

            financialStatus =
                    "SHORTAGE";


            long shortage =
                    Math.max(
                            requiredMonthlySaving
                                    - monthlySurplus,
                            0L
                    );


            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("BUDGET")

                            .title("여행자금 계획 조정이 필요합니다")

                            .description(
                                    "현재 월평균 여유자금만으로는 " +
                                            "여행 목표 달성이 어렵습니다. " +
                                            "매월 약 " +
                                            String.format(
                                                    "%,d",
                                                    shortage
                                            ) +
                                            "원의 추가 여유자금이 필요합니다."
                            )

                            .priority("HIGH")

                            .build()
            );
        }


        // =====================================================
        // 15. 지출이 수입보다 큰 경우 추가 경고
        // =====================================================
        if (
                monthlyAverageExpense
                        > monthlyAverageIncome
        ) {

            recommendations.add(

                    AssetAdviceResponseDto.RecommendationInfo
                            .builder()

                            .category("EXPENSE")

                            .title("월평균 지출 점검 필요")

                            .description(
                                    "최근 3개월 기준 월평균 지출이 " +
                                            "월평균 수입보다 높습니다. " +
                                            "여행자금 마련 전에 소비 구조를 먼저 점검해보세요."
                            )

                            .priority("HIGH")

                            .build()
            );
        }


        // =====================================================
        // 16. 최종 응답
        // =====================================================
        return AssetAdviceResponseDto.builder()

                .monthlyAverageIncome(
                        monthlyAverageIncome
                )

                .monthlyAverageExpense(
                        monthlyAverageExpense
                )

                .monthlySurplus(
                        monthlySurplus
                )

                .travelGoal(
                        travelGoalInfo
                )

                .financialStatus(
                        financialStatus
                )

                .recommendations(
                        recommendations
                )

                .build();
    }


    // =========================================================
    // null 금액 처리
    // =========================================================
    private long safeLong(Long value) {

        return value == null
                ? 0L
                : value;
    }


    // =========================================================
    // 목표 달성률
    // =========================================================
    private double calculateProgressRate(
            long currentAmount,
            long targetAmount
    ) {

        if (targetAmount <= 0) {
            return 0.0;
        }


        double rate =
                ((double) currentAmount
                        / targetAmount)
                        * 100.0;


        rate =
                Math.round(
                        rate * 10.0
                ) / 10.0;


        return Math.min(
                rate,
                100.0
        );
    }


    // =========================================================
    // 목표일까지 남은 개월
    // =========================================================
    private long calculateRemainingMonths(
            LocalDate targetDate
    ) {

        if (targetDate == null) {
            return 0L;
        }


        LocalDate today =
                LocalDate.now();


        if (!targetDate.isAfter(today)) {
            return 0L;
        }


        LocalDate currentMonth =
                today.withDayOfMonth(1);


        LocalDate targetMonth =
                targetDate.withDayOfMonth(1);


        long months =
                ChronoUnit.MONTHS.between(
                        currentMonth,
                        targetMonth
                );


        // 같은 달이지만 목표일이 아직 남아있는 경우
        if (months <= 0) {
            return 1L;
        }


        return months;
    }


    // =========================================================
    // 매월 필요한 저축액
    // =========================================================
    private long calculateRequiredMonthlySaving(
            long remainingAmount,
            long remainingMonths
    ) {

        if (remainingAmount <= 0) {
            return 0L;
        }


        if (remainingMonths <= 0) {
            return 0L;
        }


        return (long) Math.ceil(
                (double) remainingAmount
                        / remainingMonths
        );
    }
    // =====================================================
// 여행 목표 삭제
// =====================================================

    @Transactional
    public void deleteTravelGoal(
            Long userId,
            Long goalId
    ) {

        AssetGoal goal =
                assetGoalRepository
                        .findByGoalIdAndUserId(
                                goalId,
                                userId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "삭제할 여행 목표를 찾을 수 없습니다."
                                        )
                        );


        // 여행 목표만 삭제 가능
        if (
                goal.getGoalType() == null
                        ||
                        !"TRAVEL".equalsIgnoreCase(
                                goal.getGoalType()
                        )
        ) {

            throw new IllegalArgumentException(
                    "여행 목표만 삭제할 수 있습니다."
            );
        }


        assetGoalRepository.delete(
                goal
        );
    }
}