package com.example.trippaminebe.domain.nego.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** 마이리얼트립 마케팅 파트너 API 전용 HTTP 클라이언트. */
@Configuration
public class MyRealTripApiConfig {

    @Bean("myRealTripRestTemplate")
    public RestTemplate myRealTripRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }
}
