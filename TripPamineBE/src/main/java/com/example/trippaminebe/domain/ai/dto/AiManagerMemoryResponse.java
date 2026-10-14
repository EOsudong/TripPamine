package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * AI Manager가 사용할 장기 Memory 조회 응답.
 *
 * =========================================================
 * 역할
 * =========================================================
 *
 * AI_USER_FACTS의 Entity나 FACT_VALUE_JSON을
 * Controller / Frontend / LLM에 그대로 노출하지 않는다.
 *
 * 대신:
 *
 * Observed Fact
 *      ↓
 * Observation
 *
 * Pattern Candidate
 *      ↓
 * Pattern
 *
 * 형태의 안정적인 응답 모델로 변환한다.
 *
 *
 * 이 DTO는 이후 Manager LLM 연결 시에도
 * Memory Context의 경계 역할을 하게 된다.
 */
public record AiManagerMemoryResponse(

		Long userId,

		LocalDateTime generatedAt,

		List<Observation> observations,

		List<Pattern> patterns

) {


	/**
	 * 직접 관찰된 사용자 행동.
	 *
	 * 예:
	 *
	 * 사용자가 FAMILY 형태의 여행 계획을
	 * 두 번 생성했다.
	 *
	 * 이것은 아직:
	 *
	 * "FAMILY 여행을 선호한다"
	 *
	 * 라는 의미가 아니다.
	 */
	public record Observation(

			Long factId,

			String factKey,

			String domain,

			String attribute,

			String value,

			BigDecimal confidence,

			int evidenceCount,

			LocalDateTime firstObservedAt,

			LocalDateTime lastObservedAt

	) {
	}


	/**
	 * 반복된 Observation으로 계산된
	 * Pattern Candidate.
	 *
	 * 아직 Preference 확정값이 아니다.
	 *
	 * 예:
	 *
	 * FAMILY = 2
	 * ALONE  = 2
	 *
	 * 이 경우 각각의 Pattern Confidence가
	 * 경쟁 관계에 따라 계산된다.
	 */
	public record Pattern(

			Long factId,

			String factKey,

			String domain,

			String attribute,

			String value,

			int supportCount,

			int totalObservedCount,

			BigDecimal share,

			BigDecimal maturity,

			BigDecimal confidence,

			int evidenceCount,

			LocalDateTime firstObservedAt,

			LocalDateTime lastObservedAt

	) {
	}
}