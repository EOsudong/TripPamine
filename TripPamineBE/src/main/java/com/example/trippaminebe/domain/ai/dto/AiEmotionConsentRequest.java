package com.example.trippaminebe.domain.ai.dto;

import jakarta.validation.constraints.NotNull;

/** 사용자가 선택한 감정 분석 활용 여부. */
public record AiEmotionConsentRequest(@NotNull Boolean enabled) {
}
