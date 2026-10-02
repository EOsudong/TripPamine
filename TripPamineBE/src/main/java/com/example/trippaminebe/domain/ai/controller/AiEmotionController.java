package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.client.EmotionApiClient;
import com.example.trippaminebe.domain.ai.dto.EmotionAnalysisResponse;
import com.example.trippaminebe.domain.ai.dto.EmotionHealthResponse;
import com.example.trippaminebe.domain.ai.dto.EmotionPredictRequest;
import com.example.trippaminebe.domain.ai.dto.LatestEmotionResponse;
import com.example.trippaminebe.domain.ai.service.EmotionAnalysisService;
import com.example.trippaminebe.domain.ai.service.EmotionQueryService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * AI 감정 분석 API.
 *
 * /health:
 * Python AI Runtime 상태만 확인한다.
 *
 * /predict:
 * 로그인 사용자의 감정을 분석하고
 * Oracle AI Manager 테이블까지 저장한다.
 */
@RestController
@RequestMapping("/ai/emotion")
@RequiredArgsConstructor
public class AiEmotionController {

	private final EmotionApiClient
			emotionApiClient;

	private final EmotionAnalysisService
			emotionAnalysisService;

	private final EmotionQueryService
			emotionQueryService;

	@GetMapping("/health")
	public ResponseEntity<EmotionHealthResponse>
	health() {

		return ResponseEntity.ok(
				emotionApiClient.health()
		);
	}

	@PostMapping("/predict")
	public ResponseEntity<EmotionAnalysisResponse>
	predict(
			@AuthenticationPrincipal
			CustomUserDetails userDetails,

			@Valid
			@RequestBody
			EmotionPredictRequest request
	) {

		/*
		 * USER_ID를 Request Body에서 받지 않는다.
		 *
		 * JWT 인증으로 확정된 사용자만 사용함으로써
		 * 다른 USER_ID를 조작하는 것을 방지한다.
		 */
		Long userId =
				userDetails
						.getUser()
						.getId();

		EmotionAnalysisResponse response =
				emotionAnalysisService
						.analyze(
								userId,
								request
						);

		return ResponseEntity.ok(
				response
		);
	}

	/**
	 * 현재 로그인 사용자의 가장 최근 감정 상태 조회.
	 *
	 * GET /ai/emotion/latest
	 *
	 * 특정 여행 기준:
	 *
	 * GET /ai/emotion/latest?planId=12
	 */
	@GetMapping("/latest")
	public ResponseEntity<LatestEmotionResponse> latest(
			@AuthenticationPrincipal
			CustomUserDetails userDetails,

			@RequestParam(
					required = false
			)
			Long planId
	) {

		/*
		 * USER_ID를 query parameter로 받지 않는다.
		 *
		 * JWT 인증으로 확정된 사용자 ID만 사용한다.
		 */
		Long userId =
				userDetails
						.getUser()
						.getId();

		Optional<LatestEmotionResponse> latest =
				emotionQueryService
						.findLatest(
								userId,
								planId
						);

		/*
		 * 아직 감정 분석 이력이 없는 사용자는
		 * 오류가 아니므로 404보다 204 No Content를 반환한다.
		 */
		return latest
				.map(
						ResponseEntity::ok
				)
				.orElseGet(
						() ->
								ResponseEntity
										.noContent()
										.build()
				);
	}
}