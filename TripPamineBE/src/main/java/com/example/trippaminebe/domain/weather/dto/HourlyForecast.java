package com.example.trippaminebe.domain.weather.dto;

// 각 시간별 초단기예보 기록
public record HourlyForecast(
    String time,          // 표시용 시각 (예: "12시")
    Double temperature,   // 기온
    Integer humidity      // 습도
) {}
