
package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.AiManagerSettingsResponse;
import com.example.trippaminebe.domain.ai.entity.AiUserSettings;
import com.example.trippaminebe.domain.ai.repository.AiUserSettingsRepository;
import com.example.trippaminebe.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI Manager 사용자 설정 관리.
 *
 * 기본 원칙:
 * 1. 설정이 없다면 모든 선택 기능은 꺼짐
 * 2. 조회만 할 때는 DB 내용을 변경하지 않음
 * 3. 사용자가 직접 선택한 금융 설정만 변경
 * 4. 다른 AI 설정은 기존 상태 그대로 유지
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiManagerSettingsService {

	private final AiUserSettingsRepository
			aiUserSettingsRepository;

	// 기존에 존재하는 사용자 확인 및 잠금 기능 활용
	private final UserRepository
			userRepository;

	/**
	 * 기존 기능: 사용자의 현재 설정 조회.
	 */
	public AiManagerSettingsResponse getSettings(
			Long userId
	) {
		validateUserId(userId);

		return aiUserSettingsRepository
				.findById(userId)
				.map(this::toResponse)
				.orElseGet(this::defaultResponse);
	}

	/**
	 * 신규 기능: 금융 정보 활용 여부 변경.
	 *
	 * enabled = true
	 *   → 금융 요약 활용 허용
	 *
	 * enabled = false
	 *   → 금융 요약 활용 중지
	 *
	 * 다른 사용자 기록에 접근하지 않도록
	 * 로그인한 사용자의 ID만 전달받는다.
	 *
	 * @Transactional:
	 * 확인부터 저장까지 하나의 작업으로 처리한다.
	 * 중간 오류가 발생하면 변경 사항을 확정하지 않는다.
	 */
	@Transactional
	public AiManagerSettingsResponse changeFinanceConsent(
			Long userId,
			boolean enabled
	) {
		validateUserId(userId);

		/*
		 * 1. 실제 사용자 확인.
		 *
		 * 같은 사용자가 동시에 설정을 변경해도
		 * 처리가 겹치지 않도록 기존 잠금 기능을 활용한다.
		 */
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() ->
						new IllegalArgumentException(
								"사용자 정보를 찾을 수 없습니다."
						)
				);

		// Oracle 설정값은 Y 또는 N을 사용한다.
		String value = enabled ? "Y" : "N";

		/*
		 * 2. 이미 설정이 있는 경우
		 *    → 금융 정보 활용 값만 수정.
		 */
		if (aiUserSettingsRepository.existsById(userId)) {

			int changed =
					aiUserSettingsRepository
							.updateFinanceConsent(userId, value);

			if (changed != 1) {
				throw new IllegalStateException(
						"금융 정보 설정을 변경하지 못했습니다."
				);
			}

		} else if (enabled) {

			/*
			 * 3. 설정이 없고 사용자가 허용한 경우.
			 *
			 * 최초 설정을 생성한다.
			 * 다른 기능은 기본적으로 꺼져 있다.
			 */
			int created =
					aiUserSettingsRepository
							.insertFinanceConsent(userId, value);

			if (created != 1) {
				throw new IllegalStateException(
						"금융 정보 설정을 저장하지 못했습니다."
				);
			}
		}

		/*
		 * 4. 저장된 결과를 다시 조회해 반환한다.
		 *
		 * 설정이 없는 상태에서 사용 중지를 선택했다면
		 * 별도 기록을 생성할 필요가 없다.
		 */
		return getSettings(userId);
	}

	/** 감정 관측 동의를 사용자 명시적 선택에 따라서만 변경한다. */
	@Transactional
	public AiManagerSettingsResponse changeEmotionConsent(
			Long userId,
			boolean enabled
	) {
		validateUserId(userId);
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));

		String value = enabled ? "Y" : "N";
		if (aiUserSettingsRepository.existsById(userId)) {
			if (aiUserSettingsRepository.updateEmotionConsent(userId, value) != 1) {
				throw new IllegalStateException("감정 분석 설정을 변경하지 못했습니다.");
			}
		} else if (enabled) {
			if (aiUserSettingsRepository.insertEmotionConsent(userId, value) != 1) {
				throw new IllegalStateException("감정 분석 설정을 저장하지 못했습니다.");
			}
		}
		return getSettings(userId);
	}

	/**
	 * 요청한 사용자가 올바른지 확인한다.
	 */
	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new IllegalArgumentException(
					"사용자 정보를 확인해 주세요."
			);
		}
	}

	/**
	 * Oracle의 Y만 사용 허용으로 판단한다.
	 */
	private boolean isEnabled(String value) {
		return value != null
				&& "Y".equalsIgnoreCase(value.trim());
	}

	/**
	 * DB 설정을 화면에서 읽을 수 있는 형태로 변환.
	 */
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
	 * 설정 기록이 없는 사용자는 모두 꺼진 상태.
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