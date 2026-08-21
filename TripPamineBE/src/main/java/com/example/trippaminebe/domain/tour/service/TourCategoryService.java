package com.example.trippaminebe.domain.tour.service;

import com.example.trippaminebe.domain.tour.client.TourApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

/**
 * TourAPI의 "서비스 분류코드(categoryCode2)"를 이름으로 검색해서 cat1/cat2 코드를 알아내는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourCategoryService {

  private final TourApiClient tourApiClient;

  // key: "cat1|cat2" (없으면 빈 문자열), value: code -> name
  private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

  public Optional<String> resolveCat1(String... nameKeywords) {
    return findCode(loadCodes(null, null), nameKeywords);
  }

  public Optional<String> resolveCat2(String cat1, String... nameKeywords) {
    return findCode(loadCodes(cat1, null), nameKeywords);
  }

  private Optional<String> findCode(Map<String, String> codes, String... nameKeywords) {
    for (Map.Entry<String, String> entry : codes.entrySet()) {
      String name = entry.getValue();
      if (name == null) continue;
      for (String keyword : nameKeywords) {
        if (name.contains(keyword)) {
          return Optional.of(entry.getKey());
        }
      }
    }
    return Optional.empty();
  }

  private Map<String, String> loadCodes(String cat1, String cat2) {
    String cacheKey = (cat1 == null ? "" : cat1) + "|" + (cat2 == null ? "" : cat2);
    return cache.computeIfAbsent(cacheKey, key -> fetchCodes(cat1, cat2));
  }

  private Map<String, String> fetchCodes(String cat1, String cat2) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("numOfRows", "100");
    params.put("pageNo", "1");
    if (cat1 != null) params.put("cat1", cat1);
    if (cat2 != null) params.put("cat2", cat2);

    try {
      JsonNode items = tourApiClient.callList("categoryCode2", params);
      Map<String, String> result = new LinkedHashMap<>();
      StreamSupport.stream(items.spliterator(), false).forEach(item -> {
        String code = item.path("code").asText(null);
        String name = item.path("name").asText(null);
        if (code != null && name != null) {
          result.put(code, name);
        }
      });
      return result;
    } catch (Exception e) {
      log.warn("TourAPI 분류코드 조회 실패 (cat1={}, cat2={}) - 소분류 필터 없이 넓은 범위로 진행합니다.", cat1, cat2, e);
      return Map.of();
    }
  }
}
