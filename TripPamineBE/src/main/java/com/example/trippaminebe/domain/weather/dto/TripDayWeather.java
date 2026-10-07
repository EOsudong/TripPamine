package com.example.trippaminebe.domain.weather.dto;

import java.time.LocalDate;

public record TripDayWeather(
    int day, LocalDate date, String placeName, DailyWeatherSummary summary) {}