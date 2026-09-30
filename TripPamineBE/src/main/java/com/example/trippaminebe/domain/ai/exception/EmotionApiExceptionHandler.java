package com.example.trippaminebe.domain.ai.exception;

import com.example.trippaminebe.domain.ai.client.EmotionApiException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(0)
@RestControllerAdvice
public class EmotionApiExceptionHandler {

	@ExceptionHandler(
			EmotionApiException.class
	)
	public ResponseEntity<Map<String, Object>>
	handleEmotionApiException(
			EmotionApiException exception
	) {

		int upstreamStatus =
				exception.getUpstreamStatus() == null
						? 502
						: exception.getUpstreamStatus();

		HttpStatus responseStatus =
				switch (
						upstreamStatus
						) {

					case 422 ->
							HttpStatus.UNPROCESSABLE_ENTITY;

					case 503 ->
							HttpStatus.SERVICE_UNAVAILABLE;

					default ->
							HttpStatus.BAD_GATEWAY;
				};

		return ResponseEntity
				.status(
						responseStatus
				)
				.body(
						Map.of(
								"status",
								responseStatus.value(),

								"message",
								userMessage(
										responseStatus
								),

								"detail",
								exception.getMessage()
						)
				);
	}

	private String userMessage(
			HttpStatus status
	) {

		return switch (
				status
				) {

			case UNPROCESSABLE_ENTITY ->
					"감정 분석 요청 형식이 올바르지 않습니다.";

			case SERVICE_UNAVAILABLE ->
					"AI 감정 분석 서비스를 현재 사용할 수 없습니다.";

			default ->
					"AI 감정 분석 중 외부 서비스 오류가 발생했습니다.";
		};
	}
}