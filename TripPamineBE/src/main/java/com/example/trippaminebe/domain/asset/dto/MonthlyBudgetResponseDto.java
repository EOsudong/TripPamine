package com.example.trippaminebe.domain.asset.dto;

import com.example.trippaminebe.domain.asset.entity.MonthlyBudget;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MonthlyBudgetResponseDto {

    private Long budgetId;

    private Long userId;

    private Integer budgetYear;

    private Integer budgetMonth;

    private String categoryId;

    private String categoryName;

    private Long budgetAmount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    public MonthlyBudgetResponseDto(
            MonthlyBudget monthlyBudget
    ) {

        this.budgetId =
                monthlyBudget.getBudgetId();

        this.userId =
                monthlyBudget.getUserId();

        this.budgetYear =
                monthlyBudget.getBudgetYear();

        this.budgetMonth =
                monthlyBudget.getBudgetMonth();

        this.categoryId =
                monthlyBudget.getCategoryId();

        this.categoryName =
                monthlyBudget.getCategoryName();

        this.budgetAmount =
                monthlyBudget.getBudgetAmount();

        this.createdAt =
                monthlyBudget.getCreatedAt();

        this.updatedAt =
                monthlyBudget.getUpdatedAt();
    }
}