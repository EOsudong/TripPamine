package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.ai.dto.AiFinanceMonthlySummaryResponse;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
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
 * 주의: 기존 TRANSACTIONS.USERNAME은 가변 닉네임이다.
 * USER_ID 기반으로 전환하기 전에는 이름 변경 시 과거 거래의 완전한 조회를 보장할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class AiManagerFinanceSummaryLookupService {

    private final AiManagerSettingsService settingsService;
    private final AiFinanceMonthlySummaryService monthlySummaryService;
    private final AccountBookRepository accountBookRepository;
    private final UserRepository userRepository;

    public Optional<AiFinanceMonthlySummaryResponse> findOrRefreshMissing(
            Long userId, YearMonth period) {

        // 월별 요약 조회/갱신 모두 금융 활용 동의가 선행돼야 한다.
        if (!settingsService.getSettings(userId).financeInsightEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "금융 정보 활용 동의가 필요합니다."
            );
        }

        Optional<AiFinanceMonthlySummaryResponse> saved =
                monthlySummaryService.findMonthly(userId, period);
        if (saved.isPresent()) {
            return saved;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        LocalDateTime start = period.atDay(1).atStartOfDay();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay();

        // '거래 없음'과 '아직 요약 없음'을 혼동해 0원 요약을 저장하지 않는다.
        // 닉네임 변경으로 고아가 된 거래는 임의로 소유자를 추정하거나 이동하지 않는다.
        boolean hasTransactions = accountBookRepository
                .existsByUsernameAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
                        user.getUserName(), start, end);
        if (!hasTransactions) {
            return Optional.empty();
        }

        // 별도 Bean의 @Transactional 메서드 호출: 사용자 잠금 후 요약만 저장한다.
        // 경쟁 요청은 기존 refreshMonthly()의 사용자 행 잠금을 활용한다.
        return Optional.of(monthlySummaryService.refreshMonthly(userId, period));
    }
}
