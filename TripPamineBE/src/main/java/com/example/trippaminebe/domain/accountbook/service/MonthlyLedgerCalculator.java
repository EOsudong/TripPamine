package com.example.trippaminebe.domain.accountbook.service;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 월별 수입·지출의 공통 계산기.
 * 가계부 원본 리스트를 전달받아 계산하며 DB를 읽거나 수정하지 않는다.
 * 메인 AI 매니저, AI 금융 요약, 자산관리의 금액 정의를 한 곳에 둔다.
 */
@Component
public class MonthlyLedgerCalculator {

    public MonthlyTotals summarize(List<TransactionEntity> transactions) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        Map<String, BigDecimal> expensesByCategory = new TreeMap<>();

        for (TransactionEntity transaction : transactions) {
            Long recordedAmount = transaction.getAmount();
            if (recordedAmount == null || recordedAmount < 0) {
                throw new IllegalStateException("금액이 올바르지 않은 기록이 있습니다. 가계부를 확인해 주세요.");
            }

            BigDecimal amount = BigDecimal.valueOf(recordedAmount);
            String type = transaction.getType() == null
                    ? ""
                    : transaction.getType().trim().toLowerCase(Locale.ROOT);

            switch (type) {
                case "income" -> income = income.add(amount);
                case "expense" -> {
                    expense = expense.add(amount);
                    String category = transaction.getCategory() == null
                            ? "ETC"
                            : transaction.getCategory().trim().toUpperCase(Locale.ROOT);
                    if (category.isEmpty()) {
                        category = "ETC";
                    }
                    expensesByCategory.merge(category, amount, BigDecimal::add);
                }
                default -> throw new IllegalStateException(
                        "확인할 수 없는 수입·지출 기록이 있습니다. 가계부를 확인해 주세요.");
            }
        }

        // 동률일 때 분류명 오름차순으로 고정해 모든 화면에 같은 대표 항목을 표시한다.
        String topCategory = expensesByCategory.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        return new MonthlyTotals(
                income, expense, income.subtract(expense), topCategory,
                Map.copyOf(expensesByCategory), transactions.size());
    }

    public record MonthlyTotals(
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal netAmount,
            String topCategory,
            Map<String, BigDecimal> expensesByCategory,
            int sourceRecordCount
    ) {}
}
