package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiFinanceMonthlySummaryResponse;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * AI 금융 저장 요약과 실시간 원본 집계가 같은지를 비교한다.
 * Oracle NUMBER의 소수점 자릿수(0.00 등)가 달라도 실제 금액이 같으면 동일하게 처리한다.
 */
final class AiFinanceSummarySnapshotComparator {

    private AiFinanceSummarySnapshotComparator() {
        // 정적 비교 유틸리티이므로 인스턴스를 만들지 않는다.
    }

    static boolean matches(AiFinanceMonthlySummaryResponse saved,
                           AiFinanceMonthlySummaryResponse live) {
        return saved.sourceRecordCount() == live.sourceRecordCount()
                && sameAmount(saved.totalIncome(), live.totalIncome())
                && sameAmount(saved.totalExpense(), live.totalExpense())
                && sameAmount(saved.netAmount(), live.netAmount())
                && Objects.equals(saved.topCategory(), live.topCategory())
                && sameCategories(saved.expensesByCategory(), live.expensesByCategory());
    }

    private static boolean sameAmount(BigDecimal first, BigDecimal second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.compareTo(second) == 0;
    }

    private static boolean sameCategories(Map<String, BigDecimal> first,
                                          Map<String, BigDecimal> second) {
        if (first == null || second == null) {
            return first == second;
        }
        if (!first.keySet().equals(second.keySet())) {
            return false;
        }
        return first.entrySet().stream()
                .allMatch(entry -> sameAmount(entry.getValue(), second.get(entry.getKey())));
    }
}
