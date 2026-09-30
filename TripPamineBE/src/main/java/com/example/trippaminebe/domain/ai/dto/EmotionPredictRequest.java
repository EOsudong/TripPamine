package com.example.trippaminebe.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Spring Boot가 클라이언트로부터 받는 감정 분석 요청 DTO.
 *
 * planId:
 * - 여행과 관계없는 일반 Manager 대화라면 null 가능
 * - 특정 여행 중/여행 계획과 관련된 대화라면 PLAN_ID를 전달
 *
 * humanTurns:
 * - 사용자가 실제로 말한 대화 목록
 * - Python FastAPI의 human_turns 필드로 변환되어 전달된다.
 */
public record EmotionPredictRequest(

		Long planId,

		@NotNull
		@Size(
				min = 1,
				max = 50
		)
		List<
				@NotBlank
				@Size(max = 4000)
						String
				> humanTurns
) {
}