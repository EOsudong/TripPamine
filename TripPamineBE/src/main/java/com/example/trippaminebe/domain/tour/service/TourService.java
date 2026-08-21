package com.example.trippaminebe.domain.tour.service;

import com.example.trippaminebe.domain.tour.dto.TourItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 컨트롤러가 직접 의존하는 서비스. 사용자 요청 경로에서는 TourAPI를 절대 직접 호출하지 않고,
 * TourCacheService가 들고 있는 DB 캐시(TOUR_CACHE_ENTRY)만 읽는다.
 * 실제 외부 API 호출은 TourCacheRefreshScheduler가 24시간 주기로만 수행한다 — 이유와 캐시 키
 * 목록은 TourCacheService 클래스 주석을 참고.
 */
@Service
@RequiredArgsConstructor
public class TourService {

  private final TourCacheService tourCacheService;

  /** 국내 축제 및 행사: 전체 | 진행중 | 문화 예술 | 체험 | 음식 음료 */
  public List<TourItemResponse> getFestivals(String filter) {
    String normalizedFilter = normalize(filter);
    List<TourItemResponse> all = tourCacheService.getCached(TourCacheService.FESTIVALS_ALL);

    return switch (normalizedFilter) {
      case "진행중" -> all.stream().filter(item -> "ongoing".equals(item.getStatus())).collect(Collectors.toList());
      case "문화 예술", "체험", "음식 음료" ->
          all.stream().filter(item -> normalizedFilter.equals(item.getCategory())).collect(Collectors.toList());
      default -> all; // "전체"
    };
  }

  /** 국내 관광 여행지: 전체 | 체험 관광 | 역사 관광 | 자연 관광 | 문화 관광 */
  public List<TourItemResponse> getDestinations(String filter) {
    // 소분류마다 이미 서로 다른 캐시 키로 따로 적재해뒀기 때문에(TourCacheService.refreshAll 참고)
    // 여기서는 필터링 없이 바로 해당 캐시를 읽기만 하면 된다.
    return tourCacheService.getCached("destinations:" + normalize(filter));
  }

  /** 국내 관광 산업: 전체 | 숙박 | 음식 | 레저 스포츠 | 쇼핑 */
  public List<TourItemResponse> getIndustry(String filter) {
    String normalizedFilter = normalize(filter);
    if ("전체".equals(normalizedFilter)) {
      // "전체"는 물리적으로 캐시된 항목이 아니라 숙박/음식/레저 스포츠/쇼핑 4개를 합친 결과
      List<TourItemResponse> merged = new ArrayList<>();
      merged.addAll(tourCacheService.getCached("industry:숙박"));
      merged.addAll(tourCacheService.getCached("industry:음식"));
      merged.addAll(tourCacheService.getCached("industry:레저 스포츠"));
      merged.addAll(tourCacheService.getCached("industry:쇼핑"));
      return merged;
    }
    return tourCacheService.getCached("industry:" + normalizedFilter);
  }

  private String normalize(String filter) {
    return (filter == null || filter.isBlank()) ? "전체" : filter.trim();
  }
}
