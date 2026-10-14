
package com.example.trippaminebe.domain.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI_USER_SETTINGS 읽기 전용 Entity.
 *
 * 설정 행의 자동 생성이나 동의 상태 변경은 수행하지 않는다.
 */
@Entity
@Table(name = "AI_USER_SETTINGS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUserSettings {

	@Id
	@Column(name = "USER_ID")
	private Long userId;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(
			name = "MEMORY_ENABLED_YN",
			columnDefinition = "CHAR(1)"
	)
	private String memoryEnabledYn;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(
			name = "EMOTION_ENABLED_YN",
			columnDefinition = "CHAR(1)"
	)
	private String emotionEnabledYn;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(
			name = "FINANCE_INSIGHT_YN",
			columnDefinition = "CHAR(1)"
	)
	private String financeInsightYn;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(
			name = "AUTO_DIARY_YN",
			columnDefinition = "CHAR(1)"
	)
	private String autoDiaryYn;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(
			name = "PERSONALIZATION_YN",
			columnDefinition = "CHAR(1)"
	)
	private String personalizationYn;
}