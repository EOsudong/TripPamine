
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.ai.dto.AiFinanceMonthlySummaryResponse;
import com.example.trippaminebe.domain.ai.entity.AiFinancePeriodType;
import com.example.trippaminebe.domain.ai.entity.AiFinanceSummary;
import com.example.trippaminebe.domain.ai.repository.AiFinanceSummaryRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * AI Manager 월별 금융 요약 기능.
 *
 * 전체 처리 순서:
 *
 * 로그인한 사용자 확인
 *         ↓
 * 해당 월의 가계부 기록 조회
 *         ↓
 * 수입과 지출 계산
 *         ↓
 * 항목별 지출 계산
 *         ↓
 * AI_FINANCE_SUMMARIES 저장
 *         ↓
 * 계산 결과 반환
 *
 * 중요:
 * 가계부 거래 기록을 수정하지 않는다.
 * 금융 요약을 다시 계산할 때만 별도로 저장한다.
 */
@Service
@RequiredArgsConstructor
public class AiFinanceMonthlySummaryService {

	private final UserRepository userRepository;

	private final AccountBookRepository accountBookRepository;

	private final AiFinanceSummaryRepository financeSummaryRepository;

	// 기존 AI Manager와 동일한 JSON 변환 도구 사용
	private final JsonMapper jsonMapper;

	/**
	 * STEP 1: 저장된 월별 요약 조회.
	 *
	 * readOnly=true는 데이터 조회만 수행한다는 의미다.
	 * 요약이 없으면 새로 만들지 않는다.
	 */
	@Transactional(readOnly = true)
	public Optional<AiFinanceMonthlySummaryResponse> findMonthly(
			Long userId,
			YearMonth period
	) {
		validateInput(userId, period);

		return findSummary(userId, period)
				.map(this::toResponse);
	}

	/**
	 * STEP 2: 해당 월의 금융 요약 다시 계산.
	 *
	 * @Transactional:
	 * 계산 중 오류가 발생하면 저장 작업을 취소한다.
	 *
	 * 동일한 사용자가 동시에 여러 번 요청하더라도
	 * 기존 사용자 잠금 기능을 사용해 순서대로 처리한다.
	 */
	@Transactional
	public AiFinanceMonthlySummaryResponse refreshMonthly(
			Long userId,
			YearMonth period
	) {
		validateInput(userId, period);

		// 1. 로그인 사용자 확인
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() ->
						new IllegalArgumentException(
								"사용자 정보를 찾을 수 없습니다."
						)
				);

		/*
		 * 2. 계산할 기간 설정.
		 *
		 * 2026년 10월 예:
		 * 시작: 2026-10-01 00:00
		 * 종료: 2026-11-01 00:00
		 *
		 * 시작일 포함, 종료일 제외 방식으로
		 * 거래가 중복 계산되지 않도록 한다.
		 */
		LocalDateTime periodStart =
				period.atDay(1).atStartOfDay();

		LocalDateTime periodEnd =
				period.plusMonths(1).atDay(1).atStartOfDay();

		/*
		 * 3. 사용자 가계부 내역 조회.
		 *
		 * 현재 기존 가계부 구조는
		 * USERS.USER_NAME과 TRANSACTIONS.USERNAME을
		 * 연결해서 조회한다.
		 *
		 * userId를 직접 거래 테이블에 대입하지 않는다.
		 */
		List<TransactionEntity> transactions =
				accountBookRepository
						.findByUsernameAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
								user.getUserName(),
								periodStart,
								periodEnd
						);

		// 4. 계산할 금액 초기화
		BigDecimal totalIncome = BigDecimal.ZERO;
		BigDecimal totalExpense = BigDecimal.ZERO;

		/*
		 * TreeMap:
		 * 지출 항목별 금액을 보관한다.
		 *
		 * 예:
		 * FOOD      → 80000
		 * TRANSPORT → 30000
		 */
		Map<String, BigDecimal> expenseByCategory =
				new TreeMap<>();

		// 5. 가계부 기록을 하나씩 확인
		for (TransactionEntity transaction : transactions) {

			Long recordedAmount = transaction.getAmount();

			// 잘못된 금액을 임의로 계산하지 않는다.
			if (recordedAmount == null || recordedAmount < 0) {
				throw new IllegalStateException(
						"금액이 올바르지 않은 기록이 있습니다. 가계부를 확인해 주세요."
				);
			}

			BigDecimal amount =
					BigDecimal.valueOf(recordedAmount);

			String type =
					transaction.getType() == null
							? ""
							: transaction.getType()
							.trim()
							.toLowerCase(Locale.ROOT);

			switch (type) {

				// 수입이면 총수입에 더한다.
				case "income" -> {
					totalIncome = totalIncome.add(amount);
				}

				// 지출이면 총지출과 항목별 합계에 더한다.
				case "expense" -> {
					totalExpense = totalExpense.add(amount);

					String category =
							transaction.getCategory() == null
									? "ETC"
									: transaction.getCategory().trim();

					if (category.isEmpty()) {
						category = "ETC";
					}

					expenseByCategory.merge(
							category,
							amount,
							BigDecimal::add
					);
				}

				default -> throw new IllegalStateException(
						"확인할 수 없는 수입·지출 기록이 있습니다. 가계부를 확인해 주세요."
				);
			}
		}

		/*
		 * 6. 가장 지출이 많은 항목 찾기.
		 *
		 * 금액이 큰 항목부터 정렬한다.
		 * 금액이 같으면 항목 이름 순서로 정한다.
		 */
		String topCategory =
				expenseByCategory.entrySet()
						.stream()
						.sorted(
								Comparator
										.<Map.Entry<String, BigDecimal>, BigDecimal>comparing(
												Map.Entry::getValue
										)
										.reversed()
										.thenComparing(Map.Entry::getKey)
						)
						.map(Map.Entry::getKey)
						.findFirst()
						.orElse(null);

		/*
		 * 7. 기존 월별 요약 찾기.
		 *
		 * 있으면 기존 결과를 갱신하고,
		 * 없으면 새 결과를 만든다.
		 *
		 * 매번 새로운 행을 추가하지 않기 위한 처리다.
		 */
		AiFinanceSummary summary =
				findSummary(userId, period)
						.orElseGet(() ->
								AiFinanceSummary.builder()
										.user(user)
										.periodType(
												AiFinancePeriodType.MONTHLY
										)
										.periodStart(periodStart)
										.periodEnd(periodEnd)
										.build()
						);

		// 8. 계산 결과 설정
		summary.setTotalIncome(totalIncome);
		summary.setTotalExpense(totalExpense);

		summary.setNetAmount(
				totalIncome.subtract(totalExpense)
		);

		summary.setTopCategory(topCategory);

		summary.setCategorySummaryJson(
				toJson(expenseByCategory)
		);

		summary.setSourceRecordCount(
				transactions.size()
		);

		/*
		 * 9. 금융 요약만 저장한다.
		 *
		 * 실제 가계부 거래나 계좌 잔액은 변경하지 않는다.
		 * 여행 전용 필드도 이번 단계에서는 수정하지 않는다.
		 */
		AiFinanceSummary saved =
				financeSummaryRepository.save(summary);

		return toResponse(saved);
	}

	/**
	 * 지정된 사용자의 해당 월 요약 찾기.
	 *
	 * 다른 사용자, 다른 월,
	 * 특정 여행의 요약과 섞이지 않도록 한다.
	 */
	private Optional<AiFinanceSummary> findSummary(
			Long userId,
			YearMonth period
	) {
		return financeSummaryRepository
				.findFirstByUser_IdAndPeriodTypeAndPeriodStartAndPeriodEndAndTravelPlanIsNullOrderBySummaryIdDesc(
						userId,
						AiFinancePeriodType.MONTHLY,
						period.atDay(1).atStartOfDay(),
						period.plusMonths(1).atDay(1).atStartOfDay()
				);
	}

	/**
	 * 잘못된 요청을 초기에 차단한다.
	 */
	private void validateInput(
			Long userId,
			YearMonth period
	) {
		if (userId == null || userId <= 0 || period == null) {
			throw new IllegalArgumentException(
					"사용자와 조회 기간을 확인해 주세요."
			);
		}
	}

	/**
	 * 항목별 지출 결과를 DB에 저장하기 좋은
	 * 문자열 형태로 변환한다.
	 *
	 * 예:
	 * {"FOOD":80000,"TRANSPORT":30000}
	 */
	private String toJson(
			Map<String, BigDecimal> expenseByCategory
	) {
		try {
			return jsonMapper.writeValueAsString(
					expenseByCategory
			);
		} catch (RuntimeException exception) {
			throw new IllegalStateException(
					"지출 내역을 정리하지 못했습니다.",
					exception
			);
		}
	}

	/**
	 * DB에 저장된 금융 요약을
	 * 화면으로 전달하기 좋은 형식으로 변환한다.
	 */
	private AiFinanceMonthlySummaryResponse toResponse(
			AiFinanceSummary summary
	) {
		Map<String, BigDecimal> categoryTotals;

		String json = summary.getCategorySummaryJson();

		try {
			categoryTotals =
					json == null || json.isBlank()
							? Map.of()
							: jsonMapper.readValue(
							json,
							new TypeReference<Map<String, BigDecimal>>() {}
					);
		} catch (RuntimeException exception) {
			throw new IllegalStateException(
					"저장된 지출 내역을 읽지 못했습니다.",
					exception
			);
		}

		return new AiFinanceMonthlySummaryResponse(
				summary.getSummaryId(),
				summary.getPeriodStart(),
				summary.getPeriodEnd(),
				summary.getTotalIncome(),
				summary.getTotalExpense(),
				summary.getNetAmount(),
				summary.getTopCategory(),
				Map.copyOf(categoryTotals),
				summary.getSourceRecordCount()
		);
	}
}
