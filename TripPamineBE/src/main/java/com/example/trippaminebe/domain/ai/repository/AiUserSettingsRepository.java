
package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiUserSettings;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 AI 기능 설정 Repository.
 *
 * 설정 조회 시 반드시 인증된 사용자 ID를 사용한다.
 */
public interface AiUserSettingsRepository
		extends JpaRepository<AiUserSettings, Long> {
}
