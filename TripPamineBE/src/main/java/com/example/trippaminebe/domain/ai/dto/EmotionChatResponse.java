package com.example.trippaminebe.domain.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 대화 내용은 요청한 사용자에게만 반환한다. */
public record EmotionChatResponse(
        Long conversationId,
        String answer,
        String emotionName,
        LocalDateTime createdAt,
        // 실제 화면 이동은 프론트엔드에서 기능 ID로만 결정한다.
        List<String> relatedFeatureIds
) {
}
