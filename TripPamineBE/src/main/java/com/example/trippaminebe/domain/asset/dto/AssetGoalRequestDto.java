package com.example.trippaminebe.domain.asset.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AssetGoalRequestDto {

    private Long userId;

    private String goalName;

    private Long targetAmount;

    private Long currentAmount;

    private LocalDate targetDate;

    private String goalType;

    private String status;
}