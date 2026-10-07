package com.example.trippaminebe.domain.asset.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MonthlyBudgetRequestDto {

    private Long userId;

    private Integer budgetYear;

    private Integer budgetMonth;

    private String categoryId;

    private String categoryName;

    private Long budgetAmount;
}