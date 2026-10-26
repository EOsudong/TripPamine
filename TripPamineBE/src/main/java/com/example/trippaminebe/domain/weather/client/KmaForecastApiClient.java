package com.example.trippaminebe.domain.weather.client;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Component
public class KmaForecastApiClient {

  private final RestTemplate restTemplate;

  // 외부 서버가 응답하지 않을 때 요청이 무한정 대기하지 않도록 타임아웃을 건다
  public KmaForecastApiClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000); // 연결 3초
    factory.setReadTimeout(5000);    // 응답 읽기 5초
    this.restTemplate = new RestTemplate(factory);
  }

  @Value("${kma.api.base-url:}")
  private String baseUrl;

  @Value("${kma.api.key:}")
  private String serviceKey;

  public String fetchUltraShortForecast(int nx, int ny, String baseDate, String baseTime) {
    URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/getUltraSrtFcst") // 초단기예보조회
        .queryParam("serviceKey", serviceKey)
        .queryParam("pageNo", 1)
        .queryParam("numOfRows", 100) // 실제로는 6시간 x 항목10~11개 = 66줄정도지만, 넉넉하게 잡는다
        .queryParam("dataType", "JSON")
        .queryParam("base_date", baseDate)
        .queryParam("base_time", baseTime)
        .queryParam("nx", nx)
        .queryParam("ny", ny)
        .build(true) // 이미 인코딩되어 있으니 다시 인코딩하지 않는다
        .toUri();
    return restTemplate.getForObject(uri, String.class);
  } ///fetchUltraShortForecast

}
