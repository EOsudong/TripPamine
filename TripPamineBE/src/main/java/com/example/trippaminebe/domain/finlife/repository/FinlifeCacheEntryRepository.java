package com.example.trippaminebe.domain.finlife.repository;

import com.example.trippaminebe.domain.finlife.entity.FinlifeCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinlifeCacheEntryRepository extends JpaRepository<FinlifeCacheEntry, Long> {
  Optional<FinlifeCacheEntry> findByCacheKey(String cacheKey);
}
