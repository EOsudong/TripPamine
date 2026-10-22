package com.example.trippaminebe.domain.weather.service;

import com.example.trippaminebe.domain.weather.client.OpenWeatherApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentWeatherService {

  private final OpenWeatherApiClient openWeatherApiClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * GPS 좌표 기준 현재 날씨 조회
   */
  public Map<String, Object> getCurrentWeather(
      double latitude,
      double longitude
  ) {
    try {
      String json = openWeatherApiClient.fetchCurrentWeather(
          latitude,
          longitude
      );

      JsonNode root = objectMapper.readTree(json);

      // API 응답 필드 누락을 0℃ 등의 실제 날씨로 잘못 표시하지 않는다.
      if (root.path("main").path("temp").isMissingNode()
          || root.path("main").path("humidity").isMissingNode()
          || root.path("wind").path("speed").isMissingNode()
          || root.path("weather").path(0).path("description").asText("").isBlank()) {
        throw new IllegalStateException("날씨 API 응답에 필수 정보가 없습니다.");
      }

      JsonNode weatherNode = root.path("weather").path(0);

      String main = weatherNode.path("main").asText("");
      String description = weatherNode.path("description").asText("");

      String weather = switch (main.toUpperCase()) {
        case "RAIN", "DRIZZLE", "THUNDERSTORM" -> "RAIN";
        case "SNOW" -> "SNOW";
        case "CLEAR" -> "CLEAR";
        default -> "CLOUDY";
      };

      double temperature = root.path("main")
          .path("temp")
          .asDouble();

      int humidity = root.path("main")
          .path("humidity")
          .asInt();

      double windSpeed = root.path("wind")
          .path("speed")
          .asDouble();

      return Map.of(
          "weather", weather,
          "description", description,
          "temperature", temperature,
          "humidity", humidity,
          "windSpeed", windSpeed
      );

    } catch (Exception e) {
      // 위치 좌표는 개인 위치 정보이므로 장애 로그에 남기지 않는다.
      log.error("현재 날씨 조회 실패", e);
      throw new IllegalStateException(
          "현재 날씨 조회에 실패했습니다.", e
      );
    }
  }
}