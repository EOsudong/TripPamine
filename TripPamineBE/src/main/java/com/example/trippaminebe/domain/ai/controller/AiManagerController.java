package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.AiManagerContextResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerHomeResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerMemoryResponse;
import com.example.trippaminebe.domain.ai.dto.AiManagerPriorityResponse;
import com.example.trippaminebe.domain.ai.service.AiManagerContextService;
import com.example.trippaminebe.domain.ai.service.AiManagerHomeService;
import com.example.trippaminebe.domain.ai.service.AiManagerMemoryService;
import com.example.trippaminebe.domain.ai.service.AiManagerPriorityService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


/**
 * AI Manager 외부 API 진입점.
 *
 * GET /api/ai-manager/home
 *   - 화면 중심의 현재 Context
 *
 * GET /api/ai-manager/memory
 *   - 구조화된 장기 Memory
 *
 * GET /api/ai-manager/context
 *   - AI Manager 판단용 통합 Context
 *
 * GET /api/ai-manager/priority
 *   - 현재 Context 기반 Priority Engine 결과
 *
 *
 * 이후 단계:
 *
 * - Manager Chat
 * - Action
 * - Approval
 * - Execution
 *
 * 등이 추가될 수 있다.
 */
@RestController
@RequestMapping("/api/ai-manager")
@RequiredArgsConstructor
public class AiManagerController {


	private final AiManagerHomeService
			aiManagerHomeService;


	private final AiManagerMemoryService
			aiManagerMemoryService;


	private final AiManagerContextService
			aiManagerContextService;


	private final AiManagerPriorityService
			aiManagerPriorityService;


	/**
	 * 현재 로그인 사용자의
	 * AI Manager Home Context 조회.
	 *
	 * GET /api/ai-manager/home
	 */
	@GetMapping("/home")
	public AiManagerHomeResponse getHome(

			@AuthenticationPrincipal
			CustomUserDetails userDetails

	) {

		validateAuthenticatedUser(
				userDetails
		);


		return aiManagerHomeService
				.getHome(
						userDetails.getUser()
				);
	}


	/**
	 * 현재 로그인 사용자의
	 * 구조화된 장기 AI Memory 조회.
	 *
	 * GET /api/ai-manager/memory
	 */
	@GetMapping("/memory")
	public AiManagerMemoryResponse getMemory(

			@AuthenticationPrincipal
			CustomUserDetails userDetails

	) {

		validateAuthenticatedUser(
				userDetails
		);


		return aiManagerMemoryService
				.getMemory(
						userDetails
								.getUser()
								.getId()
				);
	}


	/**
	 * 현재 로그인 사용자의
	 * AI Manager 전체 Context Snapshot 조회.
	 *
	 * GET /api/ai-manager/context
	 */
	@GetMapping("/context")
	public AiManagerContextResponse getContext(

			@AuthenticationPrincipal
			CustomUserDetails userDetails

	) {

		validateAuthenticatedUser(
				userDetails
		);


		return aiManagerContextService
				.getContext(
						userDetails.getUser()
				);
	}


	/**
	 * 현재 로그인 사용자의
	 * AI Manager Priority 조회.
	 *
	 * GET /api/ai-manager/priority
	 *
	 *
	 * Priority Engine은 현재 Context를 읽고:
	 *
	 * - 무엇이 중요한가
	 * - 얼마나 중요한가
	 * - 왜 중요한가
	 *
	 * 를 구조화해서 반환한다.
	 *
	 *
	 * 현재 STEP E-1에서는
	 * Travel Priority 정책부터 적용한다.
	 *
	 * 실제 자연어 답변 생성이나
	 * Action 실행은 이 API의 책임이 아니다.
	 */
	@GetMapping("/priority")
	public AiManagerPriorityResponse getPriority(

			@AuthenticationPrincipal
			CustomUserDetails userDetails

	) {

		validateAuthenticatedUser(
				userDetails
		);


		return aiManagerPriorityService
				.evaluate(
						userDetails.getUser()
				);
	}


	/**
	 * AI Manager API 공통 인증 검증.
	 *
	 * SecurityConfig에서 인증을 요구하지만
	 * Controller 경계에서도 Principal 상태를 확인한다.
	 */
	private void validateAuthenticatedUser(
			CustomUserDetails userDetails
	) {

		if (
				userDetails == null
						||
						userDetails.getUser() == null
						||
						userDetails.getUser().getId() == null
		) {

			throw new ResponseStatusException(

					HttpStatus.UNAUTHORIZED,

					"로그인이 필요합니다."
			);
		}
	}
}