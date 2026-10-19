package com.example.trippaminebe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

   @Value("${fastapi.url:http://localhost:8000}")
   private String fastApiUrl;

   @Bean
   public WebClient fastApiWebClient() {
      // 이미지 전송을 위해 메모리 버퍼 크기 확장 (50MB)
      ExchangeStrategies strategies = ExchangeStrategies.builder()
          .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
          .build();

      return WebClient.builder()
          .baseUrl(fastApiUrl)
          .exchangeStrategies(strategies)
          .build();
   }
}