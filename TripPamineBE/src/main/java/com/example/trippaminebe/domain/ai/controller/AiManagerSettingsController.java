
package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.AiManagerSettingsResponse;
import com.example.trippaminebe.domain.ai.service.AiManagerSettingsService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * TripPamine AI Manager 사용자 설정 API.
 *
 * GET /api/ai-manager/settings
 *
 * URL 또는 요청 본문으로 userId를 받지 않는다.
 * 인증된 JWT Principal의 사용자 ID만 사용한다.
 */
@RestController
@RequestMapping("/api/ai-manager/settings")
@RequiredArgsConstructor
public class AiManagerSettingsController {

	private final AiManagerSettingsService
			aiManagerSettingsService;

	@GetMapping
	public AiManagerSettingsResponse getSettings(

			@AuthenticationPrincipal
			CustomUserDetails userDetails

	) {

		if (
				userDetails == null
						|| userDetails.getUser() == null
						|| userDetails.getUser().getId() == null
		) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"로그인이 필요합니다."
			);
		}

		return aiManagerSettingsService.getSettings(
				userDetails.getUser().getId()
		);
	}
}