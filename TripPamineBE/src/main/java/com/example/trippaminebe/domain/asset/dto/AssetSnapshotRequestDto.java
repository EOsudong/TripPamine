package com.example.trippaminebe.domain.asset.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AssetSnapshotRequestDto {

    private Long userId;

    private LocalDate snapshotDate;

    private Long totalAsset;

    private Long totalIncome;

    private Long totalExpense;

    private Long travelExpense;
}