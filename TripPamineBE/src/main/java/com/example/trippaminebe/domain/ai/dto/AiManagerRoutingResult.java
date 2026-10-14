
package com.example.trippaminebe.domain.ai.dto;

import java.util.Map;

/**
 * AI Manager Context Router의 판단 결과.
 *
 * 사용자의 발화 내용과 앱이 확인한 사실을 분리한다.
 *
 * responseMode:
 * - DIRECT_ANSWER  : 서버에서 확정된 값으로 직접 응답
 * - SLM_GENERATION : 이후 SLM 자연어 생성 단계로 전달
 *
 * 실제 SLM 호출 및 DB 저장은 수행하지 않는다.
 */
public record AiManagerRoutingResult(

		String schemaVersion,

		String intent,

		Map<String, Object> userStatedFacts,

		Map<String, Object> appVerifiedFacts,

		Decision decision

) {

	/**
	 * 응답 방법에 대한 서버 판단.
	 *
	 * directAnswer:
	 * - DIRECT_ANSWER일 때만 사용한다.
	 *
	 * nextQuestion:
	 * - 향후 추가 질문 정책에 사용한다.
	 * - 현재 단계에서는 null이다.
	 */
	public record Decision(

			String responseMode,

			String directAnswer,

			String nextQuestion

	) {
	}
}
