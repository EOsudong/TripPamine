
package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.client.ManagerSlmClient;
import com.example.trippaminebe.domain.ai.client.ManagerSlmClient.ManagerSlmHealthResponse;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI Manager SLM 연결 진단 API.
 *
 * 기존 AiManagerController와 분리한다.
 *
 * GET /api/ai-manager/slm/health
 *
 * 현재 단계:
 * - 인증 사용자 확인
 * - llama-server 준비 상태 확인
 *
 * 실제 사용자 Chat API는 다음 단계에서 구현한다.
 */
@RestController
@RequestMapping("/api/ai-manager/slm")
@RequiredArgsConstructor
public class AiManagerSlmController {

	private final ManagerSlmClient managerSlmClient;

	@GetMapping("/health")
	public ManagerSlmHealthResponse health(

			@AuthenticationPrincipal
			CustomUserDetails userDetails

	) {

		/*
		 * 기존 Manager API와 동일하게
		 * 인증된 사용자만 접근하도록 한다.
		 *
		 * JWT를 SLM 서버로 전달하지 않는다.
		 */
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

		return managerSlmClient.health();
	}
}
