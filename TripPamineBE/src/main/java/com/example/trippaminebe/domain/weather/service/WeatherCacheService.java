package com.example.trippaminebe.domain.weather.service;

import com.example.trippaminebe.domain.weather.client.OpenWeatherApiClient;
import com.example.trippaminebe.domain.weather.entity.WeatherCacheEntry;
import com.example.trippaminebe.domain.weather.repository.WeatherCacheEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherCacheService {
  private final WeatherCacheEntryRepository cacheEntryRepository;
  private final OpenWeatherApiClient openWeatherApiClient;

  private boolean isFresh(WeatherCacheEntry entry, LocalDateTime now) {
    return entry.getFetchedAt().plusDays(1).isAfter(now);
    // 날씨데이터를 받아오고 하루가 지나지 않았다면 true, 아니라면 false
  } ///isFresh

  private String fetchWeatherFromApi(double latitude, double longitude, LocalDate date) {
    return openWeatherApiClient.fetchForecast(latitude,longitude);
  } ///fetchWeatherFromApi

  // 날씨 얻어오기
  public String getWeather(double latitude, double longitude, LocalDate date) {
    String cacheKey = WeatherCacheEntry.weatherCacheKey(latitude, longitude, date);
    Optional<WeatherCacheEntry> cached = cacheEntryRepository.findByCacheKey(cacheKey);
    if (cached.isPresent()) { // 저장된 cache가 있나?
      WeatherCacheEntry entry = cached.get(); // 받아온다
      if(isFresh(entry, LocalDateTime.now())) { // 유효한가?
        return entry.getPayloadJson(); // json 응답값을 반환한다
      }
    } ///if

    // 저장된 cache가 있는데 오래되어 유효하지 않은 경우, 혹은 cache가 없는 경우
    String payload = fetchWeatherFromApi(latitude, longitude, date);
    LocalDateTime now = LocalDateTime.now();

    if(cached.isPresent()) { // 있는데 오래된 경우, 새로고침한다
      WeatherCacheEntry old = cached.get();
      old.refreshCacheEntry(payload, now);
      cacheEntryRepository.save(old);
    } else { // 저장된 cache가 없는 경우 새로 만든다
      WeatherCacheEntry created = WeatherCacheEntry.builder()
          .cacheKey(cacheKey)
          .payloadJson(payload)
          .fetchedAt(now)
          .build();
      cacheEntryRepository.save(created);
    }
    return payload;
  } ///getWeather

} ///WeatherCacheService
