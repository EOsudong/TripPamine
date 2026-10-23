package com.example.trippaminebe.domain.accountbook.service;

import java.time.YearMonth;
import java.util.Set;

/**
 * 가계부 저장·수정·삭제가 정상 커밋되면 월별 요약을 다시 계산하기 위한 이벤트.
 * 날짜가 다른 달로 변경되면 이전 달과 변경된 달을 모두 포함한다.
 */
public record LedgerChangedEvent(Long userId, Set<YearMonth> months) {
    public LedgerChangedEvent {
        months = Set.copyOf(months);
    }
}
