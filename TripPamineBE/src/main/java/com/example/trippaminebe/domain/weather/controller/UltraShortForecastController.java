package com.example.trippaminebe.domain.weather.controller;

import com.example.trippaminebe.domain.weather.dto.UltraShortForecastResult;
import com.example.trippaminebe.domain.weather.service.UltraShortForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class UltraShortForecastController {

  private final UltraShortForecastService ultraShortForecastService;

  @GetMapping("/ultra-short")
  public ResponseEntity<UltraShortForecastResult> getForecast(
      @RequestParam double lat,
      @RequestParam double lng) {
    return ResponseEntity.ok(ultraShortForecastService.getForecast(lat, lng));
  }
}
