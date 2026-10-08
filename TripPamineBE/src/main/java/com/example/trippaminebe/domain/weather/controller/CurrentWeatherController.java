package com.example.trippaminebe.domain.weather.controller;

import com.example.trippaminebe.domain.weather.service.CurrentWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class CurrentWeatherController {

  private final CurrentWeatherService currentWeatherService;

  /**
   * GPS 좌표를 기준으로 현재 날씨 조회
   *
   * GET /api/weather/current?lat=37.5665&lng=126.9780
   */
  @GetMapping("/current")
  public ResponseEntity<Map<String, Object>> getCurrentWeather(
      @RequestParam("lat") double latitude,
      @RequestParam("lng") double longitude
  ) {
    // 위도·경도 유효성 검사
    if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
        || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "올바르지 않은 GPS 좌표입니다."
      );
    }

    try {
      Map<String, Object> weather =
          currentWeatherService.getCurrentWeather(latitude, longitude);

      return ResponseEntity.ok(weather);

    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "실시간 날씨 조회에 실패했습니다.",
          e
      );
    }
  }
}