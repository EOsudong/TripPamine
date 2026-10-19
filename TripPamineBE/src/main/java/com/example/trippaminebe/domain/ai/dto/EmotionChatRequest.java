package com.example.trippaminebe.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** saveHistory=true일 때만 Oracle 대화 세션을 생성/변경한다. */
public record EmotionChatRequest(
        @NotBlank @Size(max = 2000) String message,
        Long conversationId,
        boolean saveHistory,
        boolean analyzeEmotion,
        @Min(1) int humanTurnCount,
        @Size(max = 11) List<@Valid EmotionChatTurn> history
) {
    public record EmotionChatTurn(
            @NotBlank String role,
            @NotBlank @Size(max = 2000) String content
    ) {
    }
}
