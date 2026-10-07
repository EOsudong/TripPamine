package com.example.trippaminebe.domain.weather.client;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Component
public class OpenWeatherApiClient {

  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${openweathermap.api.base-url:}")
  private String baseUrl;

  @Value("${openweathermap.api.key:}")
  private String apiKey;

  public String fetchForecast(double latitude, double longitude) {
    URI uri = UriComponentsBuilder.fromUriString(baseUrl+"/forecast")
        .queryParam("lat", latitude)
        .queryParam("lon", longitude)
        .queryParam("appid", apiKey)
        .queryParam("units", "metric") // 섭씨
        .queryParam("lang", "kr") // 한국어로 설명
        .build()
        .toUri();
    return restTemplate.getForObject(uri, String.class); //응답 JSON을 문자열로 받기
  } ///fetchForecast

} ///OpenWeatherApiClient
