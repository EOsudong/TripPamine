package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;

/**
 * 감정 분석 + DB 저장까지 완료된 최종 응답.
 *
 * 기존 EmotionPredictResponse의 모델 결과와 함께
 * 실제 Oracle에 저장된 ID도 반환한다.
 *
 * requestId는 향후 장애 추적/로그 검색용 correlation ID 역할을 한다.
 */
public record EmotionAnalysisResponse(

		String requestId,

		Long modelRunId,

		Long eventId,

		Long observationId,

		String fineLabel,

		String fineLabelName,

		String coarseLabel,

		BigDecimal confidence,

		BigDecimal first2Confidence,

		BigDecimal first2Margin,

		EmotionContextMode contextMode,

		boolean fallbackTriggered,

		int humanTurnCount,

		int turnsUsed,

		String policyVersion,

		String modelVersion,

		BigDecimal latencyMs
) {

	/**
	 * Python 모델 응답 + DB 저장 결과를
	 * 하나의 API 응답으로 합친다.
	 */
	public static EmotionAnalysisResponse from(
			String requestId,
			EmotionPredictResponse prediction,
			EmotionPersistenceResult persistence
	) {

		return new EmotionAnalysisResponse(
				requestId,
				persistence.modelRunId(),
				persistence.eventId(),
				persistence.observationId(),
				prediction.fineLabel(),
				prediction.fineLabelName(),
				prediction.coarseLabel(),
				prediction.confidence(),
				prediction.first2Confidence(),
				prediction.first2Margin(),
				prediction.contextMode(),
				prediction.fallbackTriggered(),
				prediction.humanTurnCount(),
				prediction.turnsUsed(),
				prediction.policyVersion(),
				prediction.modelVersion(),
				prediction.latencyMs()
		);
	}
}