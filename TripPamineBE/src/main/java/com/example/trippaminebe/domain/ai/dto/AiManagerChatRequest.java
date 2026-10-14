
package com.example.trippaminebe.domain.ai.dto;

/**
 * AI Manager Chat 요청.
 *
 * 현재는 단일 메시지만 처리한다.
 *
 * conversationId와 대화 이력 저장은
 * 후속 단계에서 추가한다.
 */
public record AiManagerChatRequest(
		String message
) {
}
