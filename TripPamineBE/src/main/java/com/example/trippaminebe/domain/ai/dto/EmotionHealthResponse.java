package com.example.trippaminebe.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

public record EmotionHealthResponse(

		String status,

		@JsonAlias("emotion_classifier")
		String emotionClassifier,

		@JsonAlias("policy_version")
		String policyVersion,

		@JsonAlias("model_version")
		String modelVersion,

		@JsonAlias("margin_threshold")
		BigDecimal marginThreshold
) {
}