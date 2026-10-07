package com.example.trippaminebe.domain.asset.dto;

import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class AssetGoalResponseDto {

    private Long goalId;

    private Long userId;

    private String goalName;

    private Long targetAmount;

    private Long currentAmount;

    private LocalDate targetDate;

    private String goalType;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


    public AssetGoalResponseDto(AssetGoal assetGoal) {

        this.goalId =
                assetGoal.getGoalId();

        this.userId =
                assetGoal.getUserId();

        this.goalName =
                assetGoal.getGoalName();

        this.targetAmount =
                assetGoal.getTargetAmount();

        this.currentAmount =
                assetGoal.getCurrentAmount();

        this.targetDate =
                assetGoal.getTargetDate();

        this.goalType =
                assetGoal.getGoalType();

        this.status =
                assetGoal.getStatus();

        this.createdAt =
                assetGoal.getCreatedAt();

        this.updatedAt =
                assetGoal.getUpdatedAt();
    }
}