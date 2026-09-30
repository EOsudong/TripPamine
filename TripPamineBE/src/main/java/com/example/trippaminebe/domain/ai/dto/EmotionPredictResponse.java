package com.example.trippaminebe.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

public record EmotionPredictResponse(

		@JsonAlias("fine_label")
		String fineLabel,

		@JsonAlias("fine_label_name")
		String fineLabelName,

		@JsonAlias("coarse_label")
		String coarseLabel,

		BigDecimal confidence,

		@JsonAlias("first2_confidence")
		BigDecimal first2Confidence,

		@JsonAlias("first2_margin")
		BigDecimal first2Margin,

		@JsonAlias("context_mode")
		EmotionContextMode contextMode,

		@JsonAlias("fallback_triggered")
		boolean fallbackTriggered,

		@JsonAlias("human_turn_count")
		int humanTurnCount,

		@JsonAlias("turns_used")
		int turnsUsed,

		@JsonAlias("policy_version")
		String policyVersion,

		@JsonAlias("model_version")
		String modelVersion,

		@JsonAlias("latency_ms")
		BigDecimal latencyMs
) {
}