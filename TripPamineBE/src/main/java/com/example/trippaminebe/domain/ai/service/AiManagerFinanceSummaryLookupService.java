package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.ai.dto.AiFinanceMonthlySummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

/**
 * AI 금융 질의 시 저장된 월별 요약이 없고 실제 가계부 기록이 있는 경우에만 생성한다.
 * 별도의 읽기 전용 트랜잭션을 열지 않아 refreshMonthly()의 쓰기 트랜잭션을 보장한다.
 * USER_ID로 실제 거래 존재 여부를 확인해 닉네임 변경 시에도 누락되지 않도록 한다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerFinanceSummaryLookupService {

    private final AiManagerSettingsService settingsService;
    private final AiFinanceMonthlySummaryService monthlySummaryService;
    private final AccountBookRepository accountBookRepository;

    public Optional<AiFinanceMonthlySummaryResponse> findOrRefreshMissing(
            Long userId, YearMonth period) {
        // 동의하지 않은 사용자의 금융 원본은 조회하지 않는다.
        if (!settingsService.getSettings(userId).financeInsightEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "금융 정보 활용 동의가 필요합니다.");
        }

        // 원본 합계와 저장된 요약을 비교해 데이터가 변경된 월만 재계산한다.
        Optional<AiFinanceMonthlySummaryResponse> live =
                monthlySummaryService.previewMonthly(userId, period);
        Optional<AiFinanceMonthlySummaryResponse> saved =
                monthlySummaryService.findMonthly(userId, period);

        if (live.isEmpty()) {
            // 마지막 거래를 지웠더라도 이전에 저장된 요약의 건수와 금액은 0으로 정정한다.
            if (saved.isPresent() && saved.get().sourceRecordCount() != 0) {
                monthlySummaryService.refreshMonthly(userId, period);
            }
            return Optional.empty();
        }

        if (saved.isPresent()
                && AiFinanceSummarySnapshotComparator.matches(saved.get(), live.get())) {
            return saved;
        }

        // 합계·건수·카테고리 중 하나라도 다르면 쓰기 트랜잭션에서 갱신한다.
        return Optional.of(monthlySummaryService.refreshMonthly(userId, period));
    }
}
