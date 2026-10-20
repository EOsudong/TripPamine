
package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.AiFinanceMonthlySummaryResponse;
import com.example.trippaminebe.domain.ai.service.AiFinanceMonthlySummaryService;
import com.example.trippaminebe.domain.ai.service.AiManagerSettingsService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * AI Manager 월별 금융 요약 요청 처리.
 *
 * GET:
 * 기존에 계산한 결과만 조회한다.
 *
 * POST:
 * 실제 가계부를 다시 읽어서 요약을 갱신한다.
 *
 * 사용자 ID는 외부에서 입력받지 않는다.
 * 반드시 로그인한 사용자 본인의 기록만 다룬다.
 */
@RestController
@RequestMapping("/api/ai-manager/finance/monthly")
@RequiredArgsConstructor
public class AiFinanceMonthlySummaryController {

	// 한국 시간 기준으로 이번 달을 계산한다.
	private static final ZoneId SERVICE_ZONE =
			ZoneId.of("Asia/Seoul");

	private final AiFinanceMonthlySummaryService monthlySummaryService;
	private final AiManagerSettingsService settingsService;

	/**
	 * 저장된 금융 요약 조회.
	 *
	 * 연도와 월을 입력하지 않으면 이번 달을 조회한다.
	 * 저장된 정보가 없으면 204를 반환한다.
	 */
	@GetMapping
	public ResponseEntity<AiFinanceMonthlySummaryResponse> getMonthly(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month
	) {
		Long userId = financeAllowedUserId(userDetails);
		YearMonth period = resolveMonth(year, month);

		return monthlySummaryService
				.findMonthly(userId, period)
				.map(ResponseEntity::ok)
				.orElseGet(() ->
						ResponseEntity.noContent().build()
				);
	}

	/**
	 * 월별 금융 요약 새로 계산하기.
	 *
	 * 사용자의 실제 가계부 내역을 다시 계산한다.
	 * 기존 가계부 거래와 계좌 금액은 수정하지 않는다.
	 */
	@PostMapping("/refresh")
	public AiFinanceMonthlySummaryResponse refreshMonthly(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month
	) {
		return monthlySummaryService.refreshMonthly(
				financeAllowedUserId(userDetails),
				resolveMonth(year, month)
		);
	}

	/**
	 * 금융정보 활용 동의를 확인한 후에만 저장 요약을 조회하거나 갱신한다.
	 */
	private Long financeAllowedUserId(CustomUserDetails userDetails) {
		Long userId = authenticatedUserId(userDetails);
		if (!settingsService.getSettings(userId).financeInsightEnabled()) {
			throw new ResponseStatusException(
					HttpStatus.FORBIDDEN,
					"금융 정보 활용 동의가 필요합니다."
			);
		}
		return userId;
	}

	/**
	 * 로그인한 사용자 확인.
	 *
	 * 로그인이 되어 있지 않으면
	 * 다른 사람의 금융 기록을 조회할 수 없다.
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

	/**
	 * 조회 연도와 월 확인.
	 *
	 * 둘 다 입력하지 않으면 이번 달을 사용한다.
	 * 하나만 입력하거나 잘못된 월이면 요청을 거절한다.
	 */
	private YearMonth resolveMonth(
			Integer year,
			Integer month
	) {
		if (year == null && month == null) {
			return YearMonth.now(SERVICE_ZONE);
		}

		if (year == null
				|| month == null
				|| year < 2000
				|| year > 9999) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"조회할 연도와 월을 확인해 주세요."
			);
		}

		try {
			return YearMonth.of(year, month);
		} catch (DateTimeException exception) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"조회할 연도와 월을 확인해 주세요."
			);
		}
	}
}
