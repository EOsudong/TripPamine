
package com.example.trippaminebe.domain.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * TripPamine Manager SLM HTTP Client.
 *
 * 담당:
 * - llama-server 상태 확인
 * - Qwen3 Non-Thinking 추론 요청
 * - 추론 결과의 구조 및 기본 품질 검증
 * - 비정상 생성 결과 차단
 *
 * 기존 서비스와 분리:
 * Emotion : 18001
 * Manager : 18002
 *
 * DB 조회, 사용자 인증, Context 판단,
 * 대화 저장 및 Action 실행은 담당하지 않는다.
 */
@Slf4j
@Component
public class ManagerSlmClient {

	/**
	 * 일정 길이 이상의 동일 구절이
	 * 연속 3회 이상 등장하는지 검사한다.
	 */
	private static final Pattern REPEATED_PHRASE =
			Pattern.compile(
					"(.{8,60}?)\\1{2,}",
					Pattern.DOTALL
			);

	private final RestTemplate restTemplate;

	private final String baseUrl;

	private final String model;

	public ManagerSlmClient(

			@Value(
					"${trippamine.manager.slm.base-url:http://127.0.0.1:18002}"
			)
			String configuredBaseUrl,

			@Value(
					"${trippamine.manager.slm.model:trippamine-manager-base}"
			)
			String configuredModel
	) {

		this.baseUrl = normalizeBaseUrl(configuredBaseUrl);

		if (
				configuredModel == null
						|| configuredModel.isBlank()
		) {
			throw new IllegalArgumentException(
					"Manager SLM 모델 이름이 비어 있습니다."
			);
		}

		this.model = configuredModel.trim();

		SimpleClientHttpRequestFactory factory =
				new SimpleClientHttpRequestFactory();

		factory.setConnectTimeout(3000);
		factory.setReadTimeout(30000);

		this.restTemplate = new RestTemplate(factory);
	}

	/**
	 * llama-server 모델 준비 상태 확인.
	 */
	public ManagerSlmHealthResponse health() {

		try {

			UpstreamHealthResponse response =
					restTemplate.getForObject(
							baseUrl + "/health",
							UpstreamHealthResponse.class
					);

			if (
					response == null
							|| !"ok".equalsIgnoreCase(
							response.status()
					)
			) {
				throw new ResponseStatusException(
						HttpStatus.SERVICE_UNAVAILABLE,
						"Manager SLM이 준비되지 않았습니다."
				);
			}

			return new ManagerSlmHealthResponse(
					"UP",
					model
			);

		} catch (RestClientResponseException exception) {

			throw toUpstreamException(
					exception,
					"Manager SLM Health"
			);

		} catch (ResourceAccessException exception) {

			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"Manager SLM 서버에 연결할 수 없습니다.",
					exception
			);
		}
	}

	/**
	 * Qwen3 자연어 응답 생성.
	 *
	 * 정상적인 생성 결과만 반환한다.
	 * 잘리거나 반복된 응답은 차단한다.
	 */
	public String generate(
			String systemPrompt,
			String userContent
	) {

		validateInput(systemPrompt, userContent);

		// 1. 역할 분리
		List<Map<String, String>> messages = List.of(

				Map.of(
						"role", "system",
						"content", systemPrompt
				),

				Map.of(
						"role", "user",
						"content", userContent
				)
		);

		// 2. Qwen3 Non-Thinking 요청
		Map<String, Object> request =
				new LinkedHashMap<>();

		request.put("model", model);
		request.put("messages", messages);

		request.put("temperature", 0.7);
		request.put("top_p", 0.8);
		request.put("top_k", 20);
		request.put("min_p", 0.0);
		request.put("presence_penalty", 1.5);

		request.put(
				"chat_template_kwargs",
				Map.of("enable_thinking", false)
		);

		request.put("max_tokens", 180);
		request.put("stream", false);

		try {

			UpstreamChatResponse response =
					restTemplate.postForObject(
							baseUrl + "/v1/chat/completions",
							request,
							UpstreamChatResponse.class
					);

			// 3. 응답 구조 검증
			if (
					response == null
							|| response.choices() == null
							|| response.choices().isEmpty()
			) {
				throw rejectOutput(
						"INVALID_RESPONSE_STRUCTURE"
				);
			}

			UpstreamChoice choice =
					response.choices().get(0);

			if (
					choice == null
							|| choice.message() == null
			) {
				throw rejectOutput(
						"INVALID_CHOICE"
				);
			}

			/*
			 * 4. 출력 종료 상태 검증.
			 *
			 * length:
			 * 토큰 제한으로 잘린 응답이므로 차단한다.
			 *
			 * 현재 llama.cpp에서는 정상 완료 시
			 * finish_reason=stop을 사용한다.
			 */
			String finishReason =
					choice.finishReason();

			if ("length".equalsIgnoreCase(finishReason)) {
				throw rejectOutput(
						"MAX_TOKENS_REACHED"
				);
			}

			if (!"stop".equalsIgnoreCase(finishReason)) {
				throw rejectOutput(
						"INVALID_FINISH_REASON"
				);
			}

			String answer = choice.message().content();

			// 5. 빈 답변 차단
			if (answer == null || answer.isBlank()) {
				throw rejectOutput(
						"EMPTY_ANSWER"
				);
			}

			answer = answer.trim();

			/*
			 * 6. 과도하게 긴 출력 차단.
			 *
			 * 토큰 수 이외에 문자 길이도 제한한다.
			 */
			if (answer.length() > 4000) {
				throw rejectOutput(
						"ANSWER_TOO_LONG"
				);
			}

			// 7. UTF-8 대체 문자 검출
			if (answer.indexOf('\uFFFD') >= 0) {
				throw rejectOutput(
						"INVALID_UNICODE_REPLACEMENT"
				);
			}

			/*
			 * 8. 동일 구절의 연속 반복 감지.
			 *
			 * 예:
			 * "제주도의 중심지인 "을 여러 번 반복
			 */
			if (
					REPEATED_PHRASE
							.matcher(answer)
							.find()
			) {
				throw rejectOutput(
						"REPEATED_PHRASE"
				);
			}

			/*
			 * 9. 연속 한글 음절 반복 감지.
			 *
			 * 예:
			 * "제제주주도도"
			 *
			 * 한글 한 글자가 우연히 두 번
			 * 등장했다는 이유만으로 차단하지 않는다.
			 */
			if (hasAbnormalHangulDuplication(answer)) {
				throw rejectOutput(
						"DUPLICATED_HANGUL_SYLLABLES"
				);
			}

			/*
			 * 10. 예기치 않은 키릴 문자 혼입 검사.
			 *
			 * 한국어 요청에 없었던 키릴 문자가
			 * 응답에 갑자기 나타나면 차단한다.
			 *
			 * 향후 다국어 서비스를 지원할 때는
			 * 언어 정책에 따라 재설계해야 한다.
			 */
			if (
					!containsCyrillic(userContent)
							&& containsCyrillic(answer)
			) {
				throw rejectOutput(
						"UNEXPECTED_CYRILLIC"
				);
			}

			// 모든 검증을 통과한 경우에만 반환
			return answer;

		} catch (RestClientResponseException exception) {

			throw toUpstreamException(
					exception,
					"Manager SLM Chat"
			);

		} catch (ResourceAccessException exception) {

			throw new ResponseStatusException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"Manager SLM 응답을 받을 수 없습니다.",
					exception
			);
		}
	}

	/**
	 * SLM 입력 기본 검증.
	 */
	private void validateInput(
			String systemPrompt,
			String userContent
	) {

		if (
				systemPrompt == null
						|| systemPrompt.isBlank()
						|| userContent == null
						|| userContent.isBlank()
		) {
			throw new IllegalArgumentException(
					"SLM 요청 내용이 비어 있습니다."
			);
		}

		if (
				systemPrompt.length() > 8000
						|| userContent.length() > 8000
		) {
			throw new IllegalArgumentException(
					"SLM 입력 길이 제한을 초과했습니다."
			);
		}
	}

	/**
	 * 비정상 한글 음절 반복 검출.
	 *
	 * 28자 이내에 한글 동일 음절 쌍이
	 * 3개 이상 발생할 때 이상 후보로 판단한다.
	 *
	 * 예:
	 * "먼먼저저 숙숙소소"
	 *
	 * 이 기준은 초기 휴리스틱이므로
	 * 자연스러운 문장의 오탐 가능성이 있다.
	 */
	private boolean hasAbnormalHangulDuplication(
			String text
	) {

		for (int start = 0; start < text.length(); start++) {

			int pairCount = 0;

			int end = Math.min(
					text.length() - 1,
					start + 28
			);

			for (int i = start; i < end; i++) {

				char current = text.charAt(i);

				boolean isHangulSyllable =
						current >= '\uAC00'
								&& current <= '\uD7A3';

				if (
						isHangulSyllable
								&& current == text.charAt(i + 1)
				) {

					pairCount++;
					i++;

					if (pairCount >= 3) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * 키릴 문자 여부 확인.
	 *
	 * 원문 자체는 로그에 남기지 않는다.
	 */
	private boolean containsCyrillic(String text) {

		if (text == null) {
			return false;
		}

		for (int i = 0; i < text.length(); ) {

			int codePoint =
					text.codePointAt(i);

			Character.UnicodeBlock block =
					Character.UnicodeBlock.of(codePoint);

			if (
					block == Character.UnicodeBlock.CYRILLIC
							|| block
							== Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
			) {
				return true;
			}

			i += Character.charCount(codePoint);
		}

		return false;
	}

	/**
	 * 비정상 출력 차단.
	 *
	 * 응답 텍스트와 사용자 질문은 로그에 남기지 않고
	 * 검출 사유 코드만 기록한다.
	 */
	private ResponseStatusException rejectOutput(
			String reasonCode
	) {

		log.warn(
				"Manager SLM output rejected. reason={}",
				reasonCode
		);

		return new ResponseStatusException(
				HttpStatus.BAD_GATEWAY,
				"AI 응답을 안정적으로 생성하지 못했습니다."
		);
	}

	/**
	 * 상위 서버의 HTTP 오류 변환.
	 */
	private ResponseStatusException toUpstreamException(
			RestClientResponseException exception,
			String operation
	) {

		int upstreamStatus =
				exception.getStatusCode().value();

		HttpStatus responseStatus =
				upstreamStatus == 503
						? HttpStatus.SERVICE_UNAVAILABLE
						: HttpStatus.BAD_GATEWAY;

		return new ResponseStatusException(
				responseStatus,
				operation + " 호출에 실패했습니다. HTTP="
						+ upstreamStatus,
				exception
		);
	}

	/**
	 * Base URL 검증 및 정규화.
	 */
	private String normalizeBaseUrl(String url) {

		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException(
					"Manager SLM Base URL이 비어 있습니다."
			);
		}

		String normalized = url.trim();

		if (
				!normalized.startsWith("http://")
						&& !normalized.startsWith("https://")
		) {
			throw new IllegalArgumentException(
					"Manager SLM Base URL 형식이 올바르지 않습니다."
			);
		}

		while (normalized.endsWith("/")) {

			normalized = normalized.substring(
					0,
					normalized.length() - 1
			);
		}

		return normalized;
	}

	/**
	 * Controller에 제공하는 Health 응답.
	 */
	public record ManagerSlmHealthResponse(
			String status,
			String model
	) {
	}

	/**
	 * llama-server /health 응답 DTO.
	 */
	public record UpstreamHealthResponse(
			String status
	) {
	}

	/**
	 * Chat Completions 응답 DTO.
	 */
	public record UpstreamChatResponse(
			List<UpstreamChoice> choices
	) {
	}

	/**
	 * finish_reason을 실제 llama.cpp 응답에서 읽는다.
	 */
	public record UpstreamChoice(

			UpstreamMessage message,

			@JsonProperty("finish_reason")
			String finishReason
	) {
	}

	public record UpstreamMessage(
			String role,
			String content
	) {
	}
}