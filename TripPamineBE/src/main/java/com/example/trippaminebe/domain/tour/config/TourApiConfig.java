package com.example.trippaminebe.domain.tour.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 한국관광공사 오픈API(TourAPI / 한국관광콘텐츠랩) 호출 전용 RestTemplate 빈 등록.
 * 외부 정부 API라 응답이 느릴 수 있어 커넥션/응답 타임아웃을 짧게 잡아
 * 우리 서버 요청 스레드가 무한정 붙잡히지 않도록 함.
 */
@Configuration
public class TourApiConfig {

  @Bean
  public RestTemplate tourApiRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(8000);
    return new RestTemplate(factory);
  }
}
