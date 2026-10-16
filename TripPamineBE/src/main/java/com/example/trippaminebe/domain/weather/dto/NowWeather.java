package com.example.trippaminebe.domain.weather.dto;

// SOS 모달용: 가장 가까운 시각의 날씨 요약
public record NowWeather(
    String weather,        // CLEAR / RAIN / SNOW / CLOUDY
    String description,     // "맑음", "비" 같은 화면 표시용 한글
    Double temperature      // 기온
) {}