package com.example.trippaminebe.domain.finlife.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 금융감독원 오픈API(금융상품 한눈에, FINLIFE) 호출 전용 RestTemplate 빈 등록.
 */
@Configuration
public class FinlifeApiConfig {

  @Bean
  public RestTemplate finlifeApiRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(8000);
    return new RestTemplate(factory);
  }
}
