package com.example.trippaminebe.domain.ai.support;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * AI Manager 도메인에서 사용할 기준 시간 제공 객체.
 *
 * Oracle Autonomous Database의 서버 시간대는
 * 애플리케이션 서버와 다를 수 있다.
 *
 * 특히 DB의 SYSTIMESTAMP를 그대로 TIMESTAMP 컬럼에 저장하면
 * 한국 서비스임에도 UTC 기준 시간이 들어갈 수 있다.
 *
 * 따라서 AI Manager가 사용자에게 노출하는 시각은
 * Asia/Seoul 기준으로 명시적으로 생성한다.
 */
@Component
public class AiTimeProvider {

	/**
	 * 서비스 기준 시간대.
	 *
	 * 시스템 기본 timezone에 의존하지 않고
	 * 코드에서 명확하게 Asia/Seoul을 사용한다.
	 */
	private static final ZoneId SERVICE_ZONE =
			ZoneId.of("Asia/Seoul");

	public LocalDateTime now() {

		return LocalDateTime.now(
				SERVICE_ZONE
		);
	}
}