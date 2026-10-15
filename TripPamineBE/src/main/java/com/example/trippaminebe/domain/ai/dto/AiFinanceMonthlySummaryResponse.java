
package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 사용자에게 전달할 월별 금융 요약.
 *
 * 이 파일은 응답에 어떤 정보를 포함할지 정의한다.
 *
 * 예:
 * 총수입      2,000,000원
 * 총지출        450,000원
 * 수입-지출   1,550,000원
 * 주요 지출    식비
 *
 * 이번 단계에서는 계좌 잔액과 여행 경비를
 * 합산하지 않는다. 실제로 확인된 가계부 기록만 사용한다.
 */
public record AiFinanceMonthlySummaryResponse(

		// 저장된 금융 요약의 고유 번호
		Long summaryId,

		// 조회 기간 시작일
		LocalDateTime periodStart,

		// 조회 기간 종료일
		LocalDateTime periodEnd,

		// 총수입
		BigDecimal totalIncome,

		// 총지출
		BigDecimal totalExpense,

		// 수입에서 지출을 뺀 금액
		BigDecimal netAmount,

		// 가장 지출이 많았던 항목
		String topCategory,

		// 항목별 지출 합계
		Map<String, BigDecimal> expensesByCategory,

		// 계산에 사용한 가계부 기록 수
		int sourceRecordCount

) {
}
