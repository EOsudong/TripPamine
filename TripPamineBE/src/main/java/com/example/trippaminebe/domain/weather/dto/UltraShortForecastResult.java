package com.example.trippaminebe.domain.weather.dto;

import java.util.List;

// HourlyForecast, RainAlert를 동시에 사용하기 위한 Record
public record UltraShortForecastResult(
    List<HourlyForecast> hourly,
    RainAlert rain,
    NowWeather current
) {}
