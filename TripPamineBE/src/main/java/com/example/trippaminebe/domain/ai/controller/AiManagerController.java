package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.service.AiManagerHomeService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI Manager 통합 API.
 *
 * 앞으로 Manager Home / Chat / Memory / Action 관련 API의
 * 진입점 역할을 합니다.
 */
@RestController
@RequestMapping("/api/ai-manager")
@RequiredArgsConstructor
public class AiManagerController {

	private final AiManagerHomeService aiManagerHomeService;


	/**
	 * 현재 로그인 사용자의 AI Manager Home Context.
	 *
	 * GET /api/ai-manager/home
	 */
	@GetMapping("/home")
	public AiManagerHomeResponse getHome(
			@AuthenticationPrincipal CustomUserDetails userDetails
	) {

		/*
		 * SecurityConfig의 anyRequest().authenticated() 때문에
		 * 정상 요청에서는 principal이 반드시 존재합니다.
		 *
		 * 그래도 Controller 경계에서 한 번 더 방어적으로 확인합니다.
		 */
		if (userDetails == null || userDetails.getUser() == null) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"로그인이 필요합니다."
			);
		}


		return aiManagerHomeService.getHome(
				userDetails.getUser()
		);
	}
}