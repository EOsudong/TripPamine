package com.example.trippaminebe.domain.ai.client;

import com.example.trippaminebe.domain.ai.dto.EmotionHealthResponse;
import com.example.trippaminebe.domain.ai.dto.EmotionPredictResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Component
public class EmotionApiClient {

	private final RestTemplate restTemplate;

	private final String baseUrl;

	public EmotionApiClient(
			@Qualifier("emotionApiRestTemplate")
			RestTemplate restTemplate,

			@Value("${trippamine.ai.base-url}")
			String baseUrl
	) {
		this.restTemplate =
				restTemplate;

		this.baseUrl =
				normalizeBaseUrl(
						baseUrl
				);
	}

	public EmotionHealthResponse health() {

		try {

			EmotionHealthResponse response =
					restTemplate.getForObject(
							baseUrl + "/health",
							EmotionHealthResponse.class
					);

			if (response == null) {
				throw new EmotionApiException(
						"TripPamineAI health 응답이 비어 있습니다.",
						502
				);
			}

			return response;

		} catch (
				EmotionApiException exception
		) {

			throw exception;

		} catch (
				RestClientResponseException exception
		) {

			int status =
					exception
							.getStatusCode()
							.value();

			log.warn(
					"TripPamineAI health 호출 실패. HTTP={}",
					status
			);

			throw new EmotionApiException(
					"TripPamineAI health 호출 실패. HTTP="
							+ status,
					status,
					exception
			);

		} catch (
				ResourceAccessException exception
		) {

			log.warn(
					"TripPamineAI health 연결 실패"
			);

			throw new EmotionApiException(
					"TripPamineAI에 연결할 수 없습니다.",
					503,
					exception
			);

		} catch (
				Exception exception
		) {

			log.error(
					"TripPamineAI health 호출 중 예상하지 못한 오류",
					exception
			);

			throw new EmotionApiException(
					"TripPamineAI health 호출 중 오류가 발생했습니다.",
					502,
					exception
			);
		}
	}

	public EmotionPredictResponse predict(
			List<String> humanTurns
	) {

		List<String> normalizedTurns =
				normalizeHumanTurns(
						humanTurns
				);

		UpstreamEmotionPredictRequest request =
				new UpstreamEmotionPredictRequest(
						normalizedTurns
				);

		try {

			EmotionPredictResponse response =
					restTemplate.postForObject(
							baseUrl
									+ "/api/v1/emotion/predict",
							request,
							EmotionPredictResponse.class
					);

			if (response == null) {
				throw new EmotionApiException(
						"TripPamineAI emotion 응답이 비어 있습니다.",
						502
				);
			}

			return response;

		} catch (
				EmotionApiException exception
		) {

			throw exception;

		} catch (
				RestClientResponseException exception
		) {

			int status =
					exception
							.getStatusCode()
							.value();

			log.warn(
					"TripPamineAI emotion 호출 실패. HTTP={}",
					status
			);

			throw new EmotionApiException(
					"TripPamineAI emotion 호출 실패. HTTP="
							+ status,
					status,
					exception
			);

		} catch (
				ResourceAccessException exception
		) {

			log.warn(
					"TripPamineAI emotion 연결 실패"
			);

			throw new EmotionApiException(
					"TripPamineAI 감정 분석 서버에 연결할 수 없습니다.",
					503,
					exception
			);

		} catch (
				Exception exception
		) {

			log.error(
					"TripPamineAI emotion 호출 중 예상하지 못한 오류",
					exception
			);

			throw new EmotionApiException(
					"TripPamineAI 감정 분석 호출 중 오류가 발생했습니다.",
					502,
					exception
			);
		}
	}

	private List<String> normalizeHumanTurns(
			List<String> humanTurns
	) {

		if (
				humanTurns == null
						|| humanTurns.isEmpty()
		) {

			throw new IllegalArgumentException(
					"humanTurns는 최소 1개 이상이어야 합니다."
			);
		}

		if (
				humanTurns.size()
						> 50
		) {

			throw new IllegalArgumentException(
					"humanTurns는 최대 50개까지 허용됩니다."
			);
		}

		return humanTurns
				.stream()
				.map(turn -> {

					if (turn == null) {

						throw new IllegalArgumentException(
								"humanTurns에는 null을 포함할 수 없습니다."
						);
					}

					String normalized =
							turn.trim();

					if (
							normalized.isEmpty()
					) {

						throw new IllegalArgumentException(
								"humanTurns에는 빈 문자열을 포함할 수 없습니다."
						);
					}

					if (
							normalized.length()
									> 4000
					) {

						throw new IllegalArgumentException(
								"각 humanTurn은 최대 4000자까지 허용됩니다."
						);
					}

					return normalized;
				})
				.toList();
	}

	private String normalizeBaseUrl(
			String url
	) {

		if (
				url == null
						|| url.isBlank()
		) {

			throw new IllegalArgumentException(
					"trippamine.ai.base-url이 비어 있습니다."
			);
		}

		String normalized =
				url.trim();

		while (
				normalized.endsWith("/")
		) {

			normalized =
					normalized.substring(
							0,
							normalized.length() - 1
					);
		}

		return normalized;
	}

	private record UpstreamEmotionPredictRequest(

			@JsonProperty("human_turns")
			List<String> humanTurns
	) {
	}
}