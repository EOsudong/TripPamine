
package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.AiTravelOverviewResponse;
import com.example.trippaminebe.domain.ai.service.AiTravelOverviewService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * AI 매니저 여행 현황 조회.
 *
 * 로그인한 사용자의 여행만 조회한다.
 * 저장, 수정, 삭제 기능은 포함하지 않는다.
 */
@RestController
@RequestMapping("/api/ai-manager/travel")
@RequiredArgsConstructor
public class AiTravelOverviewController {

	private final AiTravelOverviewService travelOverviewService;

	/**
	 * 사용자의 여행 현황 조회.
	 *
	 * GET /api/ai-manager/travel/overview
	 */
	@GetMapping("/overview")
	public AiTravelOverviewResponse getOverview(
			@AuthenticationPrincipal CustomUserDetails userDetails
	) {
		if (userDetails == null
				|| userDetails.getUser() == null
				|| userDetails.getUser().getId() == null) {

			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"로그인이 필요합니다."
			);
		}

		return travelOverviewService.getOverview(
				userDetails.getUser().getId()
		);
	}
}
