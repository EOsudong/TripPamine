package com.example.trippaminebe.domain.asset.repository;

import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyBudgetRepository
        extends JpaRepository<MonthlyBudget, Long> {

    // 특정 사용자의 특정 연/월 예산 조회
    List<MonthlyBudget>
    findByUserIdAndBudgetYearAndBudgetMonth(
            Long userId,
            Integer budgetYear,
            Integer budgetMonth
    );


    // [보안 수정] 본인 예산 전체 (최근 연/월 순)
    List<MonthlyBudget>
    findByUserIdOrderByBudgetYearDescBudgetMonthDesc(
            Long userId
    );


    // [보안 수정] 본인 예산만 단건 조회
    Optional<MonthlyBudget>
    findByBudgetIdAndUserId(
            Long budgetId,
            Long userId
    );

}
