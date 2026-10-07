package com.example.trippaminebe.domain.asset.dto;

import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class AssetSnapshotResponseDto {

    private Long snapshotId;

    private Long userId;

    private LocalDate snapshotDate;

    private Long totalAsset;

    private Long totalIncome;

    private Long totalExpense;

    private Long travelExpense;

    private LocalDateTime createdAt;


    public AssetSnapshotResponseDto(AssetSnapshot assetSnapshot) {

        this.snapshotId = assetSnapshot.getSnapshotId();
        this.userId = assetSnapshot.getUserId();
        this.snapshotDate = assetSnapshot.getSnapshotDate();
        this.totalAsset = assetSnapshot.getTotalAsset();
        this.totalIncome = assetSnapshot.getTotalIncome();
        this.totalExpense = assetSnapshot.getTotalExpense();
        this.travelExpense = assetSnapshot.getTravelExpense();
        this.createdAt = assetSnapshot.getCreatedAt();
    }
}