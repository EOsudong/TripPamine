
package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiUserSettings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 AI 기능 설정 저장소.
 *
 * 사용자별 설정을 조회한다.
 * 금융 설정 변경 시에는 해당 항목만 수정한다.
 *
 * 다른 설정들은 절대로 함께 변경하지 않는다.
 */
public interface AiUserSettingsRepository
		extends JpaRepository<AiUserSettings, Long> {

	/**
	 * 이미 설정 기록이 존재하는 사용자.
	 *
	 * 금융 정보 활용 여부만 변경한다.
	 * 변경한 시각도 함께 기록한다.
	 *
	 * @return 실제 수정한 행의 개수
	 */
	@Modifying
	@Query(
			value = """
            UPDATE AI_USER_SETTINGS
               SET FINANCE_INSIGHT_YN = :enabled,
                   UPDATED_AT = SYSTIMESTAMP
             WHERE USER_ID = :userId
            """,
			nativeQuery = true
	)
	int updateFinanceConsent(
			@Param("userId") Long userId,
			@Param("enabled") String enabled
	);

	/**
	 * 아직 설정 기록이 없는 사용자.
	 *
	 * 사용자가 직접 허용한 경우에만
	 * 최초 설정 기록을 생성한다.
	 *
	 * 다른 설정들은 DB 기본값인 N을 유지한다.
	 * 생성 시각과 수정 시각은 DB 기본값을 사용한다.
	 *
	 * @return 실제 추가한 행의 개수
	 */
	@Modifying
	@Query(
			value = """
            INSERT INTO AI_USER_SETTINGS (
                USER_ID,
                FINANCE_INSIGHT_YN
            ) VALUES (
                :userId,
                :enabled
            )
            """,
			nativeQuery = true
	)
	int insertFinanceConsent(
			@Param("userId") Long userId,
			@Param("enabled") String enabled
	);
}
