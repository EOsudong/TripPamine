package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.entity.LinkStatus;
import com.example.trippaminebe.domain.account.repository.AccountRepository;
import com.example.trippaminebe.domain.accountbook.service.MonthlyLedgerCalculator;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.ai.support.AiTimeProvider;
import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.repository.AssetGoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * STEP 8.8-A: AI 매니저에서 본인의 자산 장부·가계부·여행 목표를 조회한다.
 *
 * <p>조회 전 금융 정보 활용 동의를 확인하며, 은행 API 호출·스냅샷 생성·목표 변경은 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerAssetOverviewAnswerService {

    private static final int MAX_GOALS = 3;
    private static final DateTimeFormatter AS_OF_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AiManagerSettingsService settingsService;
    private final AccountRepository accountRepository;
    private final AccountBookRepository accountBookRepository;
    private final AssetGoalRepository assetGoalRepository;
    private final MonthlyLedgerCalculator monthlyLedgerCalculator;

    /** 관련 질문이 아니면 null을 반환해 기존 금융·여행 라우팅 순서를 보존한다. */
    public String findAnswer(Long userId, String message) {
        String question = normalize(message);
        boolean assetRequested = hasAssetSignal(question);
        boolean goalRequested = hasTravelGoalSignal(question);
        if ((!assetRequested && !goalRequested) || isFeatureHelp(question)) {
            return null;
        }

        // 인증된 userId에 대해서만 설정을 읽고, 동의가 없으면 금융 DB를 열지 않는다.
        if (!settingsService.getSettings(userId).financeInsightEnabled()) {
            return "금융 정보 활용에 동의하지 않아 자산·가계부·여행 목표를 조회하지 않았어요. "
                    + "AI 매니저의 '나의 자산 관리'에서 '허용하기'를 선택하면 본인 기록을 확인할 수 있습니다.";
        }

        // 변경·이체 요청은 어떤 쓰기 서비스에도 전달하지 않는다.
        if (isChangeRequest(question)) {
            return "AI 매니저의 자산 조회 기능은 읽기 전용이에요. "
                    + "계좌 이체나 목표 변경은 실행하지 않았습니다. 자산관리 화면에서 직접 확인해 주세요.";
        }

        try {
            return buildOverview(userId, assetRequested, goalRequested, question);
        } catch (RuntimeException exception) {
            // 계좌번호·금액 등 개인 재무 데이터와 상세 SQL 파라미터를 로그에 남기지 않는다.
            log.warn("AI 매니저 자산 읽기 전용 조회 실패: {}", exception.getClass().getSimpleName());
            return "자산 정보를 읽는 중 오류가 발생했어요. 데이터는 변경하지 않았습니다. "
                    + "잠시 후 자산관리 화면에서 확인해 주세요.";
        }
    }

    private String buildOverview(Long userId, boolean assetRequested, boolean goalRequested, String question) {
        LocalDateTime now = AiTimeProvider.nowInServiceZone();
        StringBuilder answer = new StringBuilder("[나의 자산·여행 목표 조회]\n")
                .append("조회 기준: ").append(now.format(AS_OF_FORMAT)).append(" (한국 시간)\n");

        if (assetRequested) {
            // 자산 대시보드 getDashboard()는 조회 중 Snapshot 저장을 수행할 수 있어 호출하지 않는다.
            // 동일한 ACTIVE 계좌 범위만 읽어 현재 장부 잔액의 합계를 계산한다.
            List<Account> accounts = accountRepository.findByUser_IdAndLinkStatus(userId, LinkStatus.ACTIVE);
            if (accounts.isEmpty()) {
                answer.append("활성화된 연동 계좌가 없어 장부 잔액 합계를 확인할 수 없습니다.\n");
            } else {
                BigDecimal balance = accounts.stream()
                        .map(Account::getBalance)
                        .filter(value -> value != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                answer.append("계좌 잔액 합계: ")
                        .append(won(balance)).append(" (활성 계좌 ")
                        .append(accounts.size()).append("개)\n");
            }

            // 이미 구현된 월별 계산기를 재사용하되, 요약 테이블에 쓰거나 갱신하지 않는다.
            YearMonth month = YearMonth.from(now);
            var transactions = accountBookRepository
                    .findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                            userId, month.atDay(1).atStartOfDay(), month.plusMonths(1).atDay(1).atStartOfDay());
            if (transactions.isEmpty()) {
                answer.append("이번 달 가계부: 기록된 거래가 없어 수입·지출이 0원이라고 단정할 수 없습니다.\n");
            } else {
                MonthlyLedgerCalculator.MonthlyTotals totals = monthlyLedgerCalculator.summarize(transactions);
                answer.append("이번 달 가계부: 수입 ").append(won(totals.totalIncome()))
                        .append(" / 지출 ").append(won(totals.totalExpense()))
                        .append(" (기록 ").append(totals.sourceRecordCount()).append("건)\n");
            }
        }

        // 자산 현황에는 활동 중인 여행 목표도 보여주되, 저장된 목표 적립액을 계좌 잔액에 더하지 않는다.
        if (assetRequested || goalRequested) {
            List<AssetGoal> goals = assetGoalRepository
                    .findByUserIdAndGoalTypeAndStatus(userId, "TRAVEL", "ACTIVE").stream()
                    .sorted(Comparator.comparing(AssetGoal::getTargetDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(AssetGoal::getGoalId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (goals.isEmpty()) {
                answer.append("진행 중인 여행 자금 목표: 등록된 목표가 없습니다.\n");
            } else {
                answer.append("진행 중인 여행 자금 목표:\n");
                for (int i = 0; i < Math.min(MAX_GOALS, goals.size()); i++) {
                    AssetGoal goal = goals.get(i);
                    String title = cleanGoalName(goal.getGoalName());
                    Long target = goal.getTargetAmount();
                    Long saved = goal.getCurrentAmount();
                    answer.append("- ").append(title).append(": ");
                    if (target == null || target <= 0 || saved == null || saved < 0) {
                        answer.append("목표 금액 또는 적립액 정보를 확인해 주세요");
                    } else {
                        BigDecimal remaining = BigDecimal.valueOf(target)
                                .subtract(BigDecimal.valueOf(saved)).max(BigDecimal.ZERO);
                        answer.append("목표 ").append(won(BigDecimal.valueOf(target)))
                                .append(" / 기록된 준비금 ").append(won(BigDecimal.valueOf(saved)))
                                .append(" / 부족분 ").append(won(remaining));
                    }
                    if (goal.getTargetDate() != null) {
                        answer.append(" / 목표일 ").append(goal.getTargetDate());
                    }
                    answer.append("\n");
                }
                if (goals.size() > MAX_GOALS) {
                    answer.append("외 ").append(goals.size() - MAX_GOALS)
                            .append("개 목표는 자산관리 화면에서 확인할 수 있습니다.\n");
                }
            }
        }

        answer.append("안내: 연동 계좌 및 가계부 입력이 반영된 장부 금액입니다. "
            + "여행 목표 준비금은 별도 기록이므로 계좌 잔액과 합산하지 않았습니다.");

        if (contains(question, "순자산")) {
            answer.append(" 대출·부채 정보가 없으므로 순자산은 계산하지 않았습니다.");
        }
        if (contains(question, "상품", "적금", "예금", "투자", "추천")) {
            answer.append(" 금융상품 적합도 판단이나 가입·매매는 아직 수행하지 않았습니다.");
        }
        return answer.toString();
    }

    private static boolean hasAssetSignal(String question) {
        return contains(question, "자산", "계좌잔액", "통장잔액", "내잔액", "나의잔액", "계좌", "내통장");
    }

    private static boolean hasTravelGoalSignal(String question) {
        return contains(question, "여행목표", "여행자금", "저축목표", "목표금액", "목표자금", "목표까지");
    }

    private static boolean isFeatureHelp(String question) {
        return contains(question, "사용법", "자산관리방법", "메뉴어디", "어디서설정", "기능소개", "기능설명", "무슨기능");
    }

    private static boolean isChangeRequest(String question) {
        return contains(question, "이체해", "송금해", "출금해", "인출해", "삭제해", "지워줘",
                "수정해", "변경해", "바꿔줘", "가입해", "매수해", "매도해", "해지해",
                "등록해", "생성해", "추가해", "저장해", "설정해");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static boolean contains(String text, String... fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) return true;
        }
        return false;
    }

    private static String cleanGoalName(String raw) {
        if (raw == null || raw.isBlank()) return "이름 없는 여행 목표";
        String cleaned = raw.replaceAll("[\\r\\n\\t]", " ").strip();
        return cleaned.length() > 60 ? cleaned.substring(0, 60) + "…" : cleaned;
    }

    private static String won(BigDecimal amount) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원";
    }
}
