package com.example.trippaminebe.domain.weather.dto;

public record RainAlert(
    boolean rainExpected,       // 비 예보 여부
    String message,            // 알림 문구
    String precipitationType,  // 강수형태 (예: "비")
    String forecastTime,       // 예상 시각 (예: "12시")
    Integer rainProbability,    // 강수확률(%)
    String rainAmount          // 강수량 (예: "1mm 미만")
) {}
