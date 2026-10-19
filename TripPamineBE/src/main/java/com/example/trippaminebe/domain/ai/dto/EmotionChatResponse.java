package com.example.trippaminebe.domain.ai.dto;

import java.time.LocalDateTime;

/** 대화 내용은 요청한 사용자에게만 반환한다. */
public record EmotionChatResponse(
        Long conversationId,
        String answer,
        String emotionName,
        LocalDateTime createdAt
) {
}
