package com.example.trippaminebe.domain.weather.service;

import com.example.trippaminebe.domain.weather.client.OpenWeatherApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

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
      throw new IllegalStateException(
          "현재 날씨 조회에 실패했습니다.", e
      );
    }
  }
}