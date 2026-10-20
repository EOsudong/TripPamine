package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiFinanceMonthlySummaryResponse;
import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * AI 매니저의 월별 가계부 질의 전용 읽기 경로.
 * 금융 동의를 확인하고, 해당 월 요약이 없을 때만 실제 가계부 기록으로 자동 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiManagerFinanceAnswerService {

    private static final int MAX_CATEGORIES = 5;

    private final AiManagerSettingsService settingsService;
    private final AiManagerFinanceSummaryLookupService summaryLookupService;

    // 기존 호출부와의 호환성 유지.
    public String findAnswer(Long userId, String message) {
        return findAnswer(userId, message, List.of());
    }

    /** 지원하는 금융 질문만 직접 처리하고, 무관한 질문은 기존 라우터로 넘긴다. */
    public String findAnswer(Long userId, String message, List<String> previousUserMessages) {
        String question = normalize(message);
        FinanceContext context = previousContext(previousUserMessages);
        boolean hasMonth = hasMonth(question);
        boolean financial = hasFinanceSignal(question);
        boolean followup = context != null && (financial || isShortFollowup(question));

        if (!(hasMonth && financial) && !followup) {
            return null;
        }

        // 여행별 예산과 월별 가계부, 게임 포인트를 섞지 않는다.
        if (contains(question, "여행", "포인트", "퀘스트", "미션", "마스코트")) {
            return "월별 가계부 지출과 여행 예산 또는 게임 포인트는 서로 다른 정보입니다. "
                    + "한 가지 조회 항목을 지정해 주세요.";
        }

        // 동의 확인 전에는 금융 요약 서비스에 접근하지 않는다.
        if (!settingsService.getSettings(userId).financeInsightEnabled()) {
            return "금융 정보 활용에 동의하지 않아 가계부 금액을 조회하지 않았어요. "
                    + "AI 매니저의 '나의 자산 관리'에서 '허용하기'를 선택하면 조회할 수 있습니다.";
        }

        YearMonth current = YearMonth.now(AiTimeProvider.SERVICE_ZONE);
        YearMonth period = requestedPeriod(question, context, current);
        Metric metric = requestedMetric(question, context);

        if (isComparison(question)) {
            try {
                return compareMonths(userId, current, metric);
            } catch (RuntimeException exception) {
                log.warn("AI 금융 월별 비교 갱신 실패", exception);
                return "가계부 요약을 비교하는 중 오류가 발생했어요. 잠시 후 다시 확인해 주세요.";
            }
        }

        Optional<AiFinanceMonthlySummaryResponse> result;
        try {
            result = summaryLookupService.findOrRefreshMissing(userId, period);
        } catch (RuntimeException exception) {
            log.warn("AI 금융 월별 요약 갱신 실패", exception);
            return "가계부 요약을 확인하는 중 오류가 발생했어요. 잠시 후 다시 확인해 주세요.";
        }
        String label = label(period);

        if (result.isEmpty()) {
            return label + "에는 현재 닉네임으로 조회 가능한 가계부 거래가 없어 "
                    + "월별 요약을 자동 생성하지 않았어요. "
                    + "닉네임을 변경했다면 이전 닉네임에 기록이 남아 있을 수 있습니다.";
        }

        AiFinanceMonthlySummaryResponse summary = result.get();
        if (summary.sourceRecordCount() <= 0) {
            return label + "의 저장된 요약에 집계된 가계부 기록이 0건입니다. "
                    + "실제 지출이 없었다는 의미는 아닙니다.";
        }

        if (isTopCategory(question)) {
            return topCategory(label, summary, metric);
        }
        if (isCategoryBreakdown(question)) {
            return categoryBreakdown(label, summary, metric);
        }

        BigDecimal amount = amount(summary, metric);
        if (amount == null) {
            return label + "의 저장된 요약에 해당 금액 정보가 없습니다.";
        }

        return label + " 가계부에 저장된 월별 요약 기준 " + metric.label + "은 "
                + won(amount) + "입니다. (집계 거래 " + summary.sourceRecordCount()
                + "건) 실제 계좌의 전체 사용액과는 다를 수 있습니다.";
    }

    private String topCategory(String label, AiFinanceMonthlySummaryResponse summary, Metric metric) {
        if (metric == Metric.INCOME) {
            return "현재 저장된 금융 요약에는 수입의 항목별 구성이 없어 확인할 수 없습니다.";
        }

        String category = summary.topCategory();
        Map<String, BigDecimal> amounts = summary.expensesByCategory();
        if (category == null || category.isBlank() || amounts == null || !amounts.containsKey(category)) {
            return label + "의 저장된 요약에 가장 큰 지출 항목 정보가 없습니다.";
        }

        BigDecimal value = amounts.get(category);
        if (value == null) {
            return label + "의 저장된 요약에 해당 항목 금액이 없습니다.";
        }

        return label + " 가계부에서 가장 많이 지출한 항목은 '" + category
                + "'이며, 합계는 " + won(value) + "입니다. "
                + "가계부에 저장된 분류명을 그대로 표시했습니다.";
    }

    private String categoryBreakdown(String label, AiFinanceMonthlySummaryResponse summary, Metric metric) {
        if (metric == Metric.INCOME) {
            return "현재 저장된 금융 요약에는 수입의 항목별 구성이 없어 확인할 수 없습니다.";
        }

        Map<String, BigDecimal> amounts = summary.expensesByCategory();
        if (amounts == null || amounts.isEmpty()) {
            return label + "의 저장된 요약에 항목별 지출 내역이 없습니다.";
        }

        List<Map.Entry<String, BigDecimal>> sorted = amounts.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Comparator
                        .<Map.Entry<String, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
        if (sorted.isEmpty()) {
            return label + "의 저장된 요약에 확인 가능한 지출 항목이 없습니다.";
        }

        StringBuilder answer = new StringBuilder(label)
                .append(" 가계부의 항목별 지출 합계입니다.\n");

        for (int i = 0; i < Math.min(sorted.size(), MAX_CATEGORIES); i++) {
            Map.Entry<String, BigDecimal> entry = sorted.get(i);
            answer.append(i + 1).append(". ")
                    .append(entry.getKey()).append(": ")
                    .append(won(entry.getValue())).append("\n");
        }
        if (sorted.size() > MAX_CATEGORIES) {
            answer.append("그 외 ").append(sorted.size() - MAX_CATEGORIES)
                    .append("개 항목은 생략했습니다.\n");
        }
        return answer.append("가계부 분류명 기준이며, 개별 거래내역이 아닌 월별 합계입니다.")
                .toString();
    }

    private String compareMonths(Long userId, YearMonth current, Metric metric) {
        YearMonth previous = current.minusMonths(1);
        Optional<AiFinanceMonthlySummaryResponse> currentSummary =
                summaryLookupService.findOrRefreshMissing(userId, current);
        Optional<AiFinanceMonthlySummaryResponse> previousSummary =
                summaryLookupService.findOrRefreshMissing(userId, previous);

        if (currentSummary.isEmpty() || previousSummary.isEmpty()) {
            return label(current) + "과 " + label(previous)
                    + "에 모두 조회 가능한 가계부 기록이 있어야 비교할 수 있어요. "
                    + "없는 월의 지출을 0원으로 가정하지 않았습니다.";
        }

        AiFinanceMonthlySummaryResponse a = currentSummary.get();
        AiFinanceMonthlySummaryResponse b = previousSummary.get();
        BigDecimal currentAmount = amount(a, metric);
        BigDecimal previousAmount = amount(b, metric);

        if (a.sourceRecordCount() <= 0 || b.sourceRecordCount() <= 0
                || currentAmount == null || previousAmount == null) {
            return "두 달 중 집계 기록 또는 금액 정보가 부족해 비교할 수 없습니다.";
        }

        int direction = currentAmount.compareTo(previousAmount);
        BigDecimal difference = currentAmount.subtract(previousAmount).abs();
        String change = direction > 0 ? "증가했습니다"
                : direction < 0 ? "감소했습니다" : "동일합니다";

        return label(previous) + " " + metric.label + " " + won(previousAmount)
                + ", " + label(current) + " " + metric.label + " " + won(currentAmount)
                + "으로, " + (direction == 0 ? "두 달의 금액이 " : won(difference) + " ")
                + change + ". 저장된 가계부 월별 요약만 비교한 결과입니다.";
    }

    private static FinanceContext previousContext(List<String> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }

        YearMonth now = YearMonth.now(AiTimeProvider.SERVICE_ZONE);
        FinanceContext context = null;
        int start = Math.max(0, history.size() - 5);
        for (int i = start; i < history.size(); i++) {
            String question = normalize(history.get(i));
            if (contains(question, "여행", "포인트", "퀘스트", "미션", "마스코트")) {
                context = null;
            } else if (hasMonth(question) && hasFinanceSignal(question)) {
                context = new FinanceContext(
                        requestedPeriod(question, null, now),
                        requestedMetric(question, null));
            } else if (context != null && (hasFinanceSignal(question) || isShortFollowup(question))) {
                context = new FinanceContext(
                        requestedPeriod(question, context, now),
                        requestedMetric(question, context));
            } else {
                context = null;
            }
        }
        return context;
    }

    private static YearMonth requestedPeriod(String question, FinanceContext context, YearMonth now) {
        if (contains(question, "지난달", "저번달", "전달")) {
            return now.minusMonths(1);
        }
        if (contains(question, "이번달", "금월")) {
            return now;
        }
        return context != null ? context.period : now;
    }

    private static Metric requestedMetric(String question, FinanceContext context) {
        if (contains(question, "수입", "번돈", "벌었", "들어온돈")
                && !contains(question, "지출", "소비", "썼", "쓴돈", "많이쓴")) {
            return Metric.INCOME;
        }
        if (contains(question, "지출", "소비", "썼", "쓴돈", "많이쓴", "얼마썼")) {
            return Metric.EXPENSE;
        }
        return context != null ? context.metric : Metric.EXPENSE;
    }

    private static boolean hasFinanceSignal(String question) {
        return contains(question, "얼마나썼", "얼마썼", "얼마를썼", "쓴돈", "돈을썼",
                "지출", "소비", "수입", "번돈", "벌었", "들어온돈", "가계부",
                "많이쓴", "큰지출", "항목별지출", "지출항목", "더썼", "덜썼");
    }

    private static boolean hasMonth(String question) {
        return contains(question, "이번달", "금월", "지난달", "저번달", "전달");
    }

    private static boolean isShortFollowup(String question) {
        String clean = question.replaceAll("[\\p{Punct}？！…]", "")
                .replaceFirst("^(그럼|그러면)", "");
        return contains(clean, "지난달은", "저번달은", "이번달은", "항목별", "카테고리별",
                "분류별", "어디에쓴", "가장많이", "제일많이", "비교", "차이",
                "늘었", "줄었", "더썼", "덜썼");
    }

    private static boolean isTopCategory(String question) {
        return contains(question, "가장많이", "제일많이", "가장큰지출", "최대지출", "큰지출");
    }

    private static boolean isCategoryBreakdown(String question) {
        return contains(question, "항목별", "카테고리별", "분류별", "지출내역", "소비내역", "어디에썼", "어디다썼");
    }

    private static boolean isComparison(String question) {
        return contains(question, "비교", "차이", "늘었", "줄었", "더썼", "덜썼", "지난달보다", "저번달보다");
    }

    private static BigDecimal amount(AiFinanceMonthlySummaryResponse summary, Metric metric) {
        return metric == Metric.INCOME ? summary.totalIncome() : summary.totalExpense();
    }

    private static String label(YearMonth period) {
        return period.getYear() + "년 " + period.getMonthValue() + "월";
    }

    private static String won(BigDecimal value) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static boolean contains(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private enum Metric {
        EXPENSE("지출"), INCOME("수입");
        private final String label;
        Metric(String label) { this.label = label; }
    }

    private record FinanceContext(YearMonth period, Metric metric) {}
}
