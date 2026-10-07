package com.example.trippaminebe.domain.weather.repository;

import com.example.trippaminebe.domain.weather.entity.WeatherCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// key로 행을 찾는 인터페이스
public interface WeatherCacheEntryRepository extends JpaRepository<WeatherCacheEntry, Long> {
  Optional<WeatherCacheEntry> findByCacheKey(String cacheKey);
}
