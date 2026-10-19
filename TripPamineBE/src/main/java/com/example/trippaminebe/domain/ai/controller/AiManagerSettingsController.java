
package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.AiFinanceConsentRequest;
import com.example.trippaminebe.domain.ai.dto.AiEmotionConsentRequest;
import com.example.trippaminebe.domain.ai.dto.AiManagerSettingsResponse;
import com.example.trippaminebe.domain.ai.service.AiManagerSettingsService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI Manager 사용자 설정 요청 처리.
 *
 * GET /api/ai-manager/settings
 *   현재 설정 확인
 *
 * PUT /api/ai-manager/settings/finance
 *   금융 정보 활용 허용 또는 중지
 *
 * 사용자 번호를 요청 주소나 내용에서 받지 않는다.
 * 로그인한 사용자의 번호만 사용한다.
 */
@RestController
@RequestMapping("/api/ai-manager/settings")
@RequiredArgsConstructor
public class AiManagerSettingsController {

	private final AiManagerSettingsService aiManagerSettingsService;

	/**
	 * 기존 기능: 현재 설정 확인.
	 */
	@GetMapping
	public AiManagerSettingsResponse getSettings(
			@AuthenticationPrincipal CustomUserDetails userDetails
	) {
		return aiManagerSettingsService.getSettings(
				authenticatedUserId(userDetails)
		);
	}

	/**
	 * 신규 기능: 금융 정보 활용 설정 변경.
	 *
	 * 사용자가 화면에서 직접 누른 값만 저장한다.
	 *
	 * 요청 내용:
	 * {"enabled": true}
	 * {"enabled": false}
	 */
	@PutMapping("/finance")
	public AiManagerSettingsResponse changeFinanceConsent(
			@AuthenticationPrincipal CustomUserDetails userDetails,

			@Valid
			@RequestBody
			AiFinanceConsentRequest request
	) {
		return aiManagerSettingsService.changeFinanceConsent(
				authenticatedUserId(userDetails),
				request.enabled()
		);
	}

	/** 현재 로그인 사용자의 감정 분석 동의만 변경한다. */
	@PutMapping("/emotion")
	public AiManagerSettingsResponse changeEmotionConsent(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@Valid @RequestBody AiEmotionConsentRequest request
	) {
		return aiManagerSettingsService.changeEmotionConsent(
				authenticatedUserId(userDetails),
				request.enabled()
		);
	}

	/**
	 * 로그인 사용자 번호 확인.
	 *
	 * 인증되지 않은 요청은 변경을 수행하지 않는다.
	 */
	private Long authenticatedUserId(
			CustomUserDetails userDetails
	) {
		if (userDetails == null
				|| userDetails.getUser() == null
				|| userDetails.getUser().getId() == null) {

			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"로그인이 필요합니다."
			);
		}

		return userDetails.getUser().getId();
	}
}