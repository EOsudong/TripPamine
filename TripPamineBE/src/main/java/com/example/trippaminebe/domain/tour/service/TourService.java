package com.example.trippaminebe.domain.tour.service;

import com.example.trippaminebe.domain.tour.client.TourApiClient;
import com.example.trippaminebe.domain.tour.client.TourApiException;
import com.example.trippaminebe.domain.tour.dto.TourDetailResponse;
import com.example.trippaminebe.domain.tour.dto.TourItemResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 컨트롤러가 직접 의존하는 서비스. 사용자 요청 경로에서는 TourAPI를 절대 직접 호출하지 않고,
 * TourCacheService가 들고 있는 DB 캐시(TOUR_CACHE_ENTRY)만 읽는다.
 * 실제 외부 API 호출은 TourCacheRefreshScheduler가 24시간 주기로만 수행한다 — 이유와 캐시 키
 * 목록은 TourCacheService 클래스 주석을 참고.
 *
 * 단, getDetail()만 예외적으로 TourApiClient를 직접 호출한다.
 */
@Service
@RequiredArgsConstructor
public class TourService {

  private static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"]+)\"");

  private final TourCacheService tourCacheService;
  private final TourApiClient tourApiClient;

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

  /**
   * 축제/여행지/관광산업 카드를 클릭했을 때 보여줄 상세 정보.
   * 1) 캐시(목록 조회 때 이미 받아둔 카테고리 라벨, 축제 기간 등)에서 먼저 찾고,
   * 2) TourAPI detailCommon2를 실시간으로 호출해서 개요/전화번호/홈페이지 등을 채워 합친다.
   *
   * contentTypeIdParam은 캐시에서 항목을 못 찾았을 때만(예: 캐시가 막 갱신돼서 목록이 바뀐 직후)
   * 프론트가 카드 클릭 시점에 들고 있던 값을 대신 사용하기 위한 보조 파라미터다.
   */
  public TourDetailResponse getDetail(String contentId, String contentTypeIdParam) {
    Optional<TourItemResponse> cachedOpt = tourCacheService.findByContentId(contentId);
    String contentTypeId = cachedOpt.map(TourItemResponse::getContentTypeId)
        .filter(id -> id != null && !id.isBlank())
        .orElse(contentTypeIdParam);

    Map<String, String> params = new LinkedHashMap<>();
    params.put("contentId", contentId);
    if (contentTypeId != null && !contentTypeId.isBlank()) {
      params.put("contentTypeId", contentTypeId);
    }
    params.put("defaultYN", "Y");
    params.put("firstImageYN", "Y");
    params.put("addrinfoYN", "Y");
    params.put("mapinfoYN", "Y");
    params.put("overviewYN", "Y");

    JsonNode detail = tourApiClient.callDetail("detailCommon2", params);

    if (detail == null && cachedOpt.isEmpty()) {
      throw new TourApiException("해당 관광정보를 찾을 수 없습니다 (contentId=" + contentId + ")");
    }

    TourItemResponse cached = cachedOpt.orElse(null);

    return TourDetailResponse.builder()
        .contentId(contentId)
        .contentTypeId(contentTypeId)
        .title(firstNonBlank(
            detail != null ? detail.path("title").asText(null) : null,
            cached != null ? cached.getTitle() : null))
        .category(cached != null ? cached.getCategory() : null)
        .address(firstNonBlank(joinAddress(detail), cached != null ? cached.getAddress() : null))
        .imageUrl(firstNonBlank(
            detail != null ? blankToNull(detail.path("firstimage").asText(null)) : null,
            cached != null ? cached.getImageUrl() : null))
        .mapX(detail != null ? numericOrNull(detail, "mapx") : (cached != null ? cached.getMapX() : null))
        .mapY(detail != null ? numericOrNull(detail, "mapy") : (cached != null ? cached.getMapY() : null))
        .tel(detail != null ? blankToNull(detail.path("tel").asText(null)) : null)
        .homepage(detail != null ? extractHomepageUrl(detail.path("homepage").asText(null)) : null)
        .overview(detail != null ? blankToNull(detail.path("overview").asText(null)) : null)
        .eventStartDate(cached != null ? cached.getEventStartDate() : null)
        .eventEndDate(cached != null ? cached.getEventEndDate() : null)
        .status(cached != null ? cached.getStatus() : null)
        .build();
  }

  private String normalize(String filter) {
    return (filter == null || filter.isBlank()) ? "전체" : filter.trim();
  }

  private String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
  }

  private String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private Double numericOrNull(JsonNode item, String field) {
    String text = item.path(field).asText("");
    return text.isBlank() ? null : item.path(field).asDouble();
  }

  private String joinAddress(JsonNode item) {
    if (item == null) return null;
    String addr1 = item.path("addr1").asText("");
    String addr2 = item.path("addr2").asText("");
    String full = (addr1 + " " + addr2).trim();
    return full.isEmpty() ? null : full;
  }

  // TourAPI 홈페이지 필드는 보통 <a href="http://xxx" target="_blank">http://xxx</a> 형태의
  // HTML 문자열로 내려와서, href 속성값만 뽑아내거나(없으면 태그만 제거한 텍스트) 순수 URL로 정리한다.
  private String extractHomepageUrl(String raw) {
    if (raw == null || raw.isBlank()) return null;
    Matcher matcher = HREF_PATTERN.matcher(raw);
    if (matcher.find()) {
      return matcher.group(1);
    }
    String stripped = raw.replaceAll("<[^>]*>", "").trim();
    return stripped.isBlank() ? null : stripped;
  }
}
