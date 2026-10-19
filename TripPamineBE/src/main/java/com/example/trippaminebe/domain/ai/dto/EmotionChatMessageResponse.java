package com.example.trippaminebe.domain.ai.dto;

import java.time.LocalDateTime;

public record EmotionChatMessageResponse(
        Long messageId,
        String role,
        String content,
        LocalDateTime createdAt
) {
}
