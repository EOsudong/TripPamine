
package com.example.trippaminebe.domain.ai.dto;

/**
 * 사용자 AI 설정 조회 응답.
 *
 * configured:
 * - true: DB에 사용자 설정 행이 존재함
 * - false: DB에 설정 행이 존재하지 않음
 *
 * 설정 행이 없다면 모든 선택적 기능을
 * 비활성화된 상태로 반환한다.
 */
public record AiManagerSettingsResponse(

		boolean configured,

		boolean memoryEnabled,

		boolean emotionEnabled,

		boolean financeInsightEnabled,

		boolean autoDiaryEnabled,

		boolean personalizationEnabled

) {
}
