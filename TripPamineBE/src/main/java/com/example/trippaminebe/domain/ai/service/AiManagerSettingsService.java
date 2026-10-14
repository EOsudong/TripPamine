
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiManagerSettingsResponse;
import com.example.trippaminebe.domain.ai.entity.AiUserSettings;
import com.example.trippaminebe.domain.ai.repository.AiUserSettingsRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TripPamine AI Manager 사용자 설정 Service.
 *
 * 원칙:
 * 1. 인증된 사용자 ID에 해당하는 설정만 조회
 * 2. 설정 행이 없다면 기본값은 모두 false
 * 3. DB에 동의 행을 자동으로 생성하지 않음
 * 4. 조회 작업에서 사용자 데이터를 변경하지 않음
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerSettingsService {

	private final AiUserSettingsRepository
			aiUserSettingsRepository;

	public AiManagerSettingsResponse getSettings(
			Long userId
	) {

		if (userId == null || userId <= 0L) {
			throw new IllegalArgumentException(
					"유효한 인증 사용자 ID가 필요합니다."
			);
		}

		return aiUserSettingsRepository
				.findById(userId)
				.map(this::toResponse)
				.orElseGet(this::defaultResponse);
	}

	/**
	 * Oracle CHAR(1)의 Y만 동의로 해석한다.
	 *
	 * N, null 및 예기치 않은 값은
	 * 동의하지 않은 상태로 처리한다.
	 */
	private boolean isEnabled(String value) {

		return value != null
				&& "Y".equalsIgnoreCase(value.trim());
	}

	private AiManagerSettingsResponse toResponse(
			AiUserSettings settings
	) {

		return new AiManagerSettingsResponse(
				true,
				isEnabled(settings.getMemoryEnabledYn()),
				isEnabled(settings.getEmotionEnabledYn()),
				isEnabled(settings.getFinanceInsightYn()),
				isEnabled(settings.getAutoDiaryYn()),
				isEnabled(settings.getPersonalizationYn())
		);
	}

	/**
	 * 미설정 사용자 기본 응답.
	 *
	 * 동의를 임의로 추정하지 않는다.
	 */
	private AiManagerSettingsResponse defaultResponse() {

		return new AiManagerSettingsResponse(
				false,
				false,
				false,
				false,
				false,
				false
		);
	}
}