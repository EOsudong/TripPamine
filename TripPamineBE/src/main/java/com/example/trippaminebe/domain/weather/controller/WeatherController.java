package com.example.trippaminebe.domain.weather.controller;

import com.example.trippaminebe.domain.weather.dto.TripDayWeather;
import com.example.trippaminebe.domain.weather.service.TripWeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor

public class WeatherController {

  private final TripWeatherService tripWeatherService;

  @GetMapping("/travel-plans/{planId}")
  public ResponseEntity<List<TripDayWeather>> getTripWeather(@PathVariable Long planId) {
    return ResponseEntity.ok(tripWeatherService.getTripDayWeather(planId));
  }
}
