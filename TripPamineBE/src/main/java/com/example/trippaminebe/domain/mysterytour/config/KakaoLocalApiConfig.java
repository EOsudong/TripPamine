package com.example.trippaminebe.domain.mysterytour.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Kakao Local API 호출 전용 HTTP 클라이언트 설정. */
@Configuration
public class KakaoLocalApiConfig {

    @Bean
    @Qualifier("kakaoLocalRestTemplate")
    public RestTemplate kakaoLocalRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }
}
