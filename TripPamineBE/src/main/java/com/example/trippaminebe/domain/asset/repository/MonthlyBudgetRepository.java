package com.example.trippaminebe.domain.asset.repository;

import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyBudgetRepository
        extends JpaRepository<MonthlyBudget, Long> {

    // 특정 사용자의 특정 연/월 예산 조회
    List<MonthlyBudget>
    findByUserIdAndBudgetYearAndBudgetMonth(
            Long userId,
            Integer budgetYear,
            Integer budgetMonth
    );
}