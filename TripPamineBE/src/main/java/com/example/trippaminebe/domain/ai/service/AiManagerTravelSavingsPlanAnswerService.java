package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.accountbook.service.MonthlyLedgerCalculator;
import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.repository.AssetGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * STEP 8.8-B: 여행 목표의 저축 계획을 본인 가계부 기록과 비교한다.
 *
 * <p>계좌 잔액을 사용 가능한 현금으로 추정하거나 목표 준비금에 중복 합산하지 않는다.
 * 금융 동의가 없는 경우 가계부·목표 저장소에 접근하지 않으며 모든 연산은 읽기 전용이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerTravelSavingsPlanAnswerService {

    private static final int HISTORY_MONTHS = 3;
    private static final int MAX_DISPLAY_GOALS = 5;

    private final AiManagerSettingsService settingsService;
    private final AssetGoalRepository assetGoalRepository;
    private final AccountBookRepository accountBookRepository;
    private final MonthlyLedgerCalculator monthlyLedgerCalculator;

    /** 저축 계획 요청만 처리하고, 단순 자산 조회와 다른 여행 질문은 기존 경로에 맡긴다. */
    public String findAnswer(Long userId, String message) {
        String question = normalize(message);
        if (!isPlanningQuestion(question)) {
            return null;
        }

        // 자산 정보 이용 동의를 확인하기 전에는 금융 원본 데이터를 읽지 않는다.
        if (!settingsService.getSettings(userId).financeInsightEnabled()) {
            return "금융 정보 활용 동의가 없어 여행 자금 계획을 계산하지 않았어요. "
                    + "AI 매니저의 '나의 자산 관리'에서 이용 여부를 선택해 주세요.";
        }

        // 자금 이동이나 목표 수정을 요청해도 시뮬레이션 조회만 지원한다.
        if (contains(question, "이체해", "송금해", "출금해", "인출해", "삭제해", "수정해",
                "변경해", "바꿔줘", "가입해", "매수해", "매도해", "해지해")) {
            return "여행 자금 분석은 읽기 전용입니다. 이체·목표 변경·상품 가입을 실행하지 않았어요.";
        }

        try {
            return buildPlan(userId, question);
        } catch (RuntimeException exception) {
            // 거래 금액, 목표 이름, 계좌 정보 등 개인 금융 데이터를 운영 로그에 남기지 않는다.
            log.warn("AI 매니저 여행 자금 시뮬레이션 실패: {}", exception.getClass().getSimpleName());
            return "여행 자금 계획을 계산하는 중 오류가 발생했어요. "
                    + "기존 자산이나 여행 목표는 변경하지 않았습니다.";
        }
    }

    private String buildPlan(Long userId, String question) {
        LocalDate today = AiTimeProvider.nowInServiceZone().toLocalDate();
        List<AssetGoal> goals = assetGoalRepository
                .findByUserIdAndGoalTypeAndStatus(userId, "TRAVEL", "ACTIVE")
                .stream()
                .sorted(Comparator.comparing(AssetGoal::getTargetDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AssetGoal::getGoalId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        if (goals.isEmpty()) {
            return "진행 중인 여행 자금 목표가 없습니다. "
                    + "먼저 자산관리에서 목표 금액과 목표일을 등록해 주세요.";
        }

        // 특정 여행명을 말한 경우 해당 목표를 상세 표시하지만,
        // 다른 목표들의 필요 적립액도 전체 부담 계산에 포함해 중복 배분을 피한다.
        List<AssetGoal> selected = selectGoals(goals, question);
        List<GoalPlan> allPlans = goals.stream().map(goal -> planForGoal(goal, today)).toList();
        List<GoalPlan> selectedPlans = allPlans.stream()
                .filter(plan -> selected.contains(plan.goal()))
                .toList();

        StringBuilder answer = new StringBuilder("[나의 여행 자금 달성 시뮬레이션]\n")
                .append("분석 기준: ").append(today).append(" (한국 시간)\n")
                .append("월 적립 횟수는 다음 달부터 목표월까지 매월 1일에 적립하는 가정입니다. ")
                .append("목표일이 이번 달이면 별도 즉시 마련 금액으로 표시합니다.\n");

        for (int i = 0; i < Math.min(selectedPlans.size(), MAX_DISPLAY_GOALS); i++) {
            GoalPlan plan = selectedPlans.get(i);
            AssetGoal goal = plan.goal();
            answer.append("- ").append(cleanName(goal.getGoalName())).append(": ");
            if (!plan.valid()) {
                answer.append("목표 금액·현재 준비금 정보 확인 필요");
            } else {
                answer.append("목표 ").append(won(BigDecimal.valueOf(goal.getTargetAmount())))
                        .append(" / 준비금 ").append(won(BigDecimal.valueOf(goal.getCurrentAmount())))
                        .append(" / 부족분 ").append(won(plan.remaining()));
                if (plan.remaining().signum() == 0) {
                    answer.append(" / 기록상 목표금액 달성");
                } else if (goal.getTargetDate() == null) {
                    answer.append(" / 목표일 미등록: 월 적립액 계산 불가");
                } else if (!goal.getTargetDate().isAfter(today)) {
                    answer.append(" / 목표일 경과 또는 당일: 즉시 확인 필요");
                } else if (plan.months() == 0) {
                    answer.append(" / 이번 달 안에 마련할 금액 ").append(won(plan.remaining()));
                } else {
                    answer.append(" / 목표일 ").append(goal.getTargetDate())
                            .append(" / 월 필요 저축액 ").append(won(plan.monthlyRequired()))
                            .append(" × ").append(plan.months()).append("회");
                }
            }
            answer.append("\n");
        }
        if (selectedPlans.size() > MAX_DISPLAY_GOALS) {
            answer.append("외 ").append(selectedPlans.size() - MAX_DISPLAY_GOALS)
                    .append("개 여행 목표의 상세 내용은 자산관리에서 확인할 수 있습니다.\n");
        }

        // 모든 활성 목표의 월 필요액을 합산하되, 만기가 임박해 일시 마련해야 하는 금액은 분리한다.
        BigDecimal allMonthlyRequired = BigDecimal.ZERO;
        BigDecimal urgent = BigDecimal.ZERO;
        int invalidCount = 0;
        for (GoalPlan plan : allPlans) {
            if (!plan.valid()) {
                invalidCount++;
            } else if (plan.remaining().signum() > 0) {
                if (plan.months() > 0) {
                    allMonthlyRequired = allMonthlyRequired.add(plan.monthlyRequired());
                } else if (plan.goal().getTargetDate() != null) {
                    urgent = urgent.add(plan.remaining());
                }
            }
        }
        answer.append("전체 활성 여행 목표 기준 월 필요 적립액 합계(기간별 변동 전): ")
                .append(won(allMonthlyRequired)).append("\n");
        if (urgent.signum() > 0) {
            answer.append("이번 달 마감·기한 경과 목표의 별도 부족분: ")
                    .append(won(urgent)).append("\n");
        }
        if (invalidCount > 0) {
            answer.append("금액을 확인할 수 없는 목표 ").append(invalidCount)
                    .append("개는 위 합계에서 제외했습니다.\n");
        }

        if (contains(question, "가장먼저", "우선순위")) {
            // 달성 예상 순위가 아니라 등록된 목표 날짜 순서임을 명확히 구분한다.
            AssetGoal firstDeadline = goals.stream()
                    .filter(goal -> goal.getTargetDate() != null && !goal.getTargetDate().isBefore(today))
                    .findFirst().orElse(null);
            if (firstDeadline != null) {
                answer.append("등록된 목표일이 가장 빠른 여행: ")
                        .append(cleanName(firstDeadline.getGoalName()))
                        .append(" (").append(firstDeadline.getTargetDate())
                        .append("). 이는 마감 순서이며 실제 달성 순위는 아닙니다.\n");
            }
        }

        Cashflow cashflow = analyzeLastCompletedMonths(userId, today);
        if (cashflow.recordedMonths() == 0) {
            answer.append("완료된 최근 3개월의 가계부 기록이 없어 월별 저축 여력을 비교할 수 없습니다.\n");
        } else if (cashflow.recordedMonths() < HISTORY_MONTHS) {
            // 일부 월만 기록되었다면 누락된 월을 0원으로 간주해 월평균을 왜곡하지 않는다.
            answer.append("완료된 최근 3개월 중 거래 기록이 있는 달: ")
                    .append(cashflow.recordedMonths()).append("/3개월\n")
                    .append("기록된 거래 합계: 수입 ").append(won(cashflow.totalIncome()))
                    .append(" / 지출 ").append(won(cashflow.totalExpense()))
                    .append(" / 차액 ").append(won(cashflow.totalNet())).append("\n")
                    .append("기록이 없는 달이 있어 3개월 월평균과 여행 목표 달성 가능성은 계산하지 않았습니다. ")
                    .append("표시 금액은 등록된 가계부 거래에 한정됩니다.\n");
        } else {
            // 세 달 모두 거래가 있는 경우에만 기록 기준 월평균을 계산한다.
            BigDecimal divisor = BigDecimal.valueOf(HISTORY_MONTHS);
            BigDecimal averageIncome = cashflow.totalIncome()
                    .divide(divisor, 0, RoundingMode.HALF_UP);
            BigDecimal averageExpense = cashflow.totalExpense()
                    .divide(divisor, 0, RoundingMode.HALF_UP);
            BigDecimal averageNet = cashflow.totalNet()
                    .divide(divisor, 0, RoundingMode.HALF_UP);
            answer.append("완료된 최근 3개월 가계부 기록 기준 월평균: 수입 ")
                    .append(won(averageIncome)).append(" / 지출 ")
                    .append(won(averageExpense))
                    .append(" / 차액 ").append(won(averageNet))
                    .append(" (거래 기록 있는 달 3/3)\n");
            if (allMonthlyRequired.signum() > 0) {
                BigDecimal gap = allMonthlyRequired.subtract(averageNet);
                if (gap.signum() > 0) {
                    answer.append("단순 기록 차액 대비 월 추가 확보 필요액: ")
                            .append(won(gap)).append("\n");
                } else {
                    answer.append("가계부 평균 차액이 월 필요 적립액 이상입니다. ")
                            .append("다만 실제 적립 가능성이나 목표 달성을 보장하지는 않습니다.\n");
                }
            }
        }
        answer.append("안내: 기록된 여행 준비금은 계좌 잔액과 별도이며 다시 합산하지 않았습니다. ")
                .append("가계부 차액은 실제 사용 가능 현금이 아니고, 비상자금·부채·예정 지출·")
                .append("이미 저축한 금액의 출처를 확인하지 않았으므로 조건부 참고 계산입니다. ")
                .append("목표나 계좌 데이터는 변경하지 않았습니다.");
        return answer.toString();
    }

    private Cashflow analyzeLastCompletedMonths(Long userId, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth start = current.minusMonths(HISTORY_MONTHS);
        List<TransactionEntity> transactions = accountBookRepository
                .findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                        userId, start.atDay(1).atStartOfDay(), current.atDay(1).atStartOfDay());

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        int covered = 0;
        for (int i = 0; i < HISTORY_MONTHS; i++) {
            YearMonth month = start.plusMonths(i);
            List<TransactionEntity> monthRecords = transactions.stream()
                    .filter(tx -> tx.getTransactionDate() != null
                            && YearMonth.from(tx.getTransactionDate()).equals(month))
                    .toList();
            if (monthRecords.isEmpty()) continue;
            covered++;
            MonthlyLedgerCalculator.MonthlyTotals totals = monthlyLedgerCalculator.summarize(monthRecords);
            income = income.add(totals.totalIncome());
            expense = expense.add(totals.totalExpense());
        }
        // 저장소에서 확인된 거래 합계를 보존하고, 월평균은 충분한 기록이 있을 때만 계산한다.
        return new Cashflow(income, expense, income.subtract(expense), covered);
    }

    private static GoalPlan planForGoal(AssetGoal goal, LocalDate today) {
        Long target = goal.getTargetAmount();
        Long saved = goal.getCurrentAmount();
        if (target == null || target <= 0 || saved == null || saved < 0) {
            return new GoalPlan(goal, false, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        }
        BigDecimal remaining = BigDecimal.valueOf(target)
                .subtract(BigDecimal.valueOf(saved)).max(BigDecimal.ZERO);
        LocalDate deadline = goal.getTargetDate();
        long months = deadline == null || !deadline.isAfter(today)
                ? 0 : ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(deadline));
        BigDecimal required = months > 0
                ? remaining.divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING)
                : BigDecimal.ZERO;
        return new GoalPlan(goal, true, remaining, months, required);
    }

    private static List<AssetGoal> selectGoals(List<AssetGoal> goals, String question) {
        List<AssetGoal> matches = new ArrayList<>();
        for (AssetGoal goal : goals) {
            String name = normalize(goal.getGoalName());
            // '여행'처럼 지나치게 짧고 일반적인 이름은 전체 분석 요청을 잘못 가로채지 않는다.
            if (name.length() >= 3 && !name.equals("여행") && question.contains(name)) {
                matches.add(goal);
            }
        }
        return matches.isEmpty() ? goals : List.copyOf(matches);
    }

    private static boolean isPlanningQuestion(String question) {
        if (!contains(question, "여행", "목표", "자금")) return false;
        if (contains(question, "사용법", "메뉴", "기능소개", "기능설명")) return false;
        return contains(question, "얼마씩", "매월", "매달", "저축해야", "저축하면", "모을", "달성",
                "저축계획", "자금계획", "가능할", "가능해", "계산해", "시뮬레이션",
                "소비패턴", "지출패턴", "여유자금", "우선순위", "가장먼저", "언제모",
                "어떻게준비", "준비하면", "어떻게저축", "분석해", "계획세워");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static boolean contains(String text, String... parts) {
        for (String part : parts) {
            if (text.contains(part)) return true;
        }
        return false;
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) return "이름 없는 여행 목표";
        String cleaned = name.replaceAll("[\\r\\n\\t]", " ").strip();
        return cleaned.length() > 60 ? cleaned.substring(0, 60) + "…" : cleaned;
    }

    private static String won(BigDecimal value) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원";
    }

    private record GoalPlan(AssetGoal goal, boolean valid, BigDecimal remaining,
                            long months, BigDecimal monthlyRequired) {}

    private record Cashflow(BigDecimal totalIncome, BigDecimal totalExpense,
                            BigDecimal totalNet, int recordedMonths) {}
}
