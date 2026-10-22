package com.example.trippaminebe.domain.tour.repository;

import com.example.trippaminebe.domain.tour.entity.TourCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TourCacheEntryRepository extends JpaRepository<TourCacheEntry, Long> {
  Optional<TourCacheEntry> findByCacheKey(String cacheKey);

  // 여러 캐시 키를 한 번의 Oracle 조회로 읽어 네트워크 왕복 횟수를 줄입니다.
  List<TourCacheEntry> findByCacheKeyIn(List<String> cacheKeys);
}
