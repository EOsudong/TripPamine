
package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.AiManagerChatRequest;
import com.example.trippaminebe.domain.ai.dto.AiManagerChatResponse;
import com.example.trippaminebe.domain.ai.service.AiManagerChatService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI Manager Chat API V1.
 *
 * POST /api/ai-manager/chat
 *
 * 인증된 사용자만 요청할 수 있다.
 *
 * JWT와 사용자 식별자는
 * Manager SLM 서버에 전달하지 않는다.
 */
@RestController
@RequestMapping("/api/ai-manager/chat")
@RequiredArgsConstructor
public class AiManagerChatController {

	private final AiManagerChatService
			aiManagerChatService;

	@PostMapping
	public AiManagerChatResponse chat(

			@AuthenticationPrincipal
			CustomUserDetails userDetails,

			@RequestBody(required = false)
			AiManagerChatRequest request

	) {

		/*
		 * 기존 AI Manager API와 동일한
		 * 인증 사용자 검증 방식.
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

		if (request == null) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"요청 본문이 필요합니다."
			);
		}

		return aiManagerChatService.chat(
				userDetails.getUser(),
				request.message()
		);
	}
}
