
package com.example.trippaminebe.domain.ai.dto;

/**
 * AI Manager Chat 응답.
 *
 * intent:
 * - Router가 분류한 질문 유형
 *
 * responseMode:
 * - DIRECT_ANSWER
 * - SLM_GENERATION
 *
 * answer:
 * - 사용자에게 표시할 최종 문장
 */
public record AiManagerChatResponse(

		String intent,

		String responseMode,

		String answer

) {
}
