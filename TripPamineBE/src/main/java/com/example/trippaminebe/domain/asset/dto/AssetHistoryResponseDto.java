package com.example.trippaminebe.domain.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetHistoryResponseDto {

    // WEEK / MONTH / QUARTER / YEAR
    private String period;

    // 조회 시작일
    private LocalDate startDate;

    // 조회 종료일
    private LocalDate endDate;

    // 기간 시작 시점의 추정 자산
    private Long startAsset;

    // 현재 자산
    private Long currentAsset;

    // 자산 증감액
    private Long changeAmount;

    // 자산 증감률
    private Double changeRate;

    // 조회 기간 총수입
    private Long totalIncome;

    // 조회 기간 총지출
    private Long totalExpense;

    // 순현금흐름
    // totalIncome - totalExpense
    private Long netCashFlow;

    // 자산 변화 그래프
    @Builder.Default
    private List<AssetPoint> assetHistory = new ArrayList<>();

    // 수입 / 지출 그래프
    @Builder.Default
    private List<CashFlowPoint> cashFlow = new ArrayList<>();


    // =====================================================
    // 자산 그래프 Point
    // =====================================================

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetPoint {

        // 그래프 날짜
        private LocalDate date;

        // 해당 날짜의 자산
        private Long amount;
    }


    // =====================================================
    // 현금 흐름 그래프 Point
    // =====================================================

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashFlowPoint {

        // 그래프 날짜
        private LocalDate date;

        // 해당 구간 수입
        private Long income;

        // 해당 구간 지출
        private Long expense;
    }
}