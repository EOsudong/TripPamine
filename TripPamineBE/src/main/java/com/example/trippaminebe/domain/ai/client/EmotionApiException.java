package com.example.trippaminebe.domain.ai.client;

import lombok.Getter;

@Getter
public class EmotionApiException
		extends RuntimeException {

	private final Integer upstreamStatus;

	public EmotionApiException(
			String message,
			Integer upstreamStatus
	) {
		super(message);
		this.upstreamStatus =
				upstreamStatus;
	}

	public EmotionApiException(
			String message,
			Integer upstreamStatus,
			Throwable cause
	) {
		super(
				message,
				cause
		);

		this.upstreamStatus =
				upstreamStatus;
	}
}