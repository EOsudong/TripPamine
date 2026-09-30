package com.example.trippaminebe.domain.ai.dto;

/**
 * 감정 분석 1회를 DB에 저장한 뒤 생성된 PK 묶음.
 *
 * 하나의 감정 분석으로 다음 세 레코드가 연결된다.
 *
 * AI_MODEL_RUNS
 *      ↓
 * AI_MANAGER_EVENTS
 *      ↓
 * AI_EMOTION_OBSERVATIONS
 */
public record EmotionPersistenceResult(

		Long modelRunId,

		Long eventId,

		Long observationId
) {
}