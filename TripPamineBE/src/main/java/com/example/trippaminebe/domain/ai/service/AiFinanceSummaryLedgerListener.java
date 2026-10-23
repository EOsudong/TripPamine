package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.accountbook.service.LedgerChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.YearMonth;

/**
 * 가계부 원본 저장이 성공적으로 커밋된 뒤에만 금융 요약을 동기화한다.
 * 요약 갱신 실패가 이미 커밋된 가계부 거래를 실패로 오인하게 하지 않도록
 * 오류를 기록하고, 다음 홈/금융 조회에서도 원본과 비교할 수 있게 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiFinanceSummaryLedgerListener {

    private final AiManagerSettingsService settingsService;
    private final AiFinanceMonthlySummaryService monthlySummaryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLedgerChanged(LedgerChangedEvent event) {
        // 사용자가 금융 활용을 허용하지 않았다면 요약을 생성/갱신하지 않는다.
        try {
            if (!settingsService.getSettings(event.userId()).financeInsightEnabled()) {
                return;
            }
        } catch (RuntimeException exception) {
            log.warn("금융 요약 갱신 전 설정 조회 실패: userId={}", event.userId(), exception);
            return;
        }

        for (YearMonth month : event.months()) {
            try {
                // REQUIRES_NEW: 커밋이 종료된 가계부 원본을 새 트랜잭션에서 조회한다.
                monthlySummaryService.refreshMonthly(event.userId(), month);
            } catch (RuntimeException exception) {
                log.warn("가계부 변경 후 금융 요약 재집계 실패: userId={} month={}",
                        event.userId(), month, exception);
            }
        }
    }
}
