package com.example.trippaminebe.domain.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class EmotionApiConfig {

	@Bean
	@Qualifier("emotionApiRestTemplate")
	public RestTemplate emotionApiRestTemplate(
			@Value("${trippamine.ai.connect-timeout-ms:3000}")
			int connectTimeoutMs,

			@Value("${trippamine.ai.read-timeout-ms:30000}")
			int readTimeoutMs
	) {

		SimpleClientHttpRequestFactory factory =
				new SimpleClientHttpRequestFactory();

		factory.setConnectTimeout(
				connectTimeoutMs
		);

		factory.setReadTimeout(
				readTimeoutMs
		);

		return new RestTemplate(
				factory
		);
	}
}