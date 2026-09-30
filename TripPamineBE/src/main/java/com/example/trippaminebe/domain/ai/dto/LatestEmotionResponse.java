package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI Manager가 사용할 "현재 사용자의 가장 최근 감정 상태" 응답 DTO.
 *
 * 감정 결과만 전달하는 것이 아니라
 * 어떤 모델 실행에서 만들어진 결과인지 추적할 수 있도록
 * ModelRun 정보도 함께 제공한다.
 *
 * 이후 AI Manager Home에서는 이 DTO를 그대로 내부 조립 재료로 사용할 수 있다.
 */
public record LatestEmotionResponse(

		// 실제 감정 관측 데이터의 PK
		Long observationId,

		// 감정 분석 이벤트의 PK
		Long eventId,

		// 감정을 만든 모델 실행의 PK
		Long modelRunId,

		// 전체 시스템에서 모델 실행을 추적하기 위한 UUID
		String requestId,

		// 특정 여행과 관련된 감정이면 PLAN_ID,
		// 일반 Manager 대화라면 null
		Long planId,

		// 세부 감정 코드. 예: E63
		String fineLabel,

		// 사람이 읽을 수 있는 세부 감정명. 예: 편안한
		String fineLabelName,

		// 상위 감정 그룹. 예: 기쁨
		String coarseLabel,

		// 최종 모델 confidence
		BigDecimal confidence,

		// 실제 감정을 판단한 모델 버전
		String modelVersion,

		// FIRST2 / fallback 정책 버전
		String policyVersion,

		// FIRST2 또는 FULL_FALLBACK
		String contextMode,

		// 전체 문맥 재분석이 실제 실행되었는지 여부
		boolean fallbackTriggered,

		// 감정이 실제 관측된 시각
		LocalDateTime observedAt
) {
}