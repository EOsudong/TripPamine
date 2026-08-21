package com.example.trippaminebe.domain.tour.repository;

import com.example.trippaminebe.domain.tour.entity.TourCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TourCacheEntryRepository extends JpaRepository<TourCacheEntry, Long> {
  Optional<TourCacheEntry> findByCacheKey(String cacheKey);
}
