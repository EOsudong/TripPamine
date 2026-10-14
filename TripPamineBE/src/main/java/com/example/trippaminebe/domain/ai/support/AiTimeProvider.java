package com.example.trippaminebe.domain.ai.support;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;


/**
 * AI Manager 도메인의 기준 시간을 제공한다.
 *
 * =========================================================
 * 기준
 * =========================================================
 *
 * TripPamine 서비스의 AI Manager 시간은
 * Asia/Seoul 기준으로 통일한다.
 *
 * Oracle Autonomous Database 자체는 UTC를 사용할 수 있고,
 * 개발 PC / 운영 서버의 JVM timezone 역시 달라질 수 있다.
 *
 * 따라서:
 *
 * LocalDateTime.now()
 *
 * 처럼 JVM 기본 timezone에 의존하지 않고
 *
 * Asia/Seoul
 *
 * 을 명시적으로 사용한다.
 */
@Component
public class AiTimeProvider {

	/**
	 * TripPamine 서비스 기준 시간대.
	 *
	 * Entity의 JPA lifecycle fallback에서도
	 * 동일한 기준을 사용할 수 있도록 public으로 둔다.
	 */
	public static final ZoneId SERVICE_ZONE =
			ZoneId.of("Asia/Seoul");


	/**
	 * Spring Service에서 주입받아 사용할 때의 API.
	 */
	public LocalDateTime now() {

		return nowInServiceZone();
	}


	/**
	 * Spring Bean 주입이 불가능한 JPA Entity에서도
	 * 동일한 서비스 시간을 사용할 수 있도록 제공한다.
	 *
	 * Entity는 Spring Bean이 아니므로
	 * AiTimeProvider를 생성자 주입할 수 없다.
	 */
	public static LocalDateTime nowInServiceZone() {

		return LocalDateTime.now(
				SERVICE_ZONE
		);
	}
}