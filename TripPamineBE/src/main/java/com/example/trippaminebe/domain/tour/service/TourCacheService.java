package com.example.trippaminebe.domain.tour.service;

import com.example.trippaminebe.domain.tour.client.TourApiClient;
import com.example.trippaminebe.domain.tour.dto.TourItemResponse;
import com.example.trippaminebe.domain.tour.entity.TourCacheEntry;
import com.example.trippaminebe.domain.tour.repository.TourCacheEntryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * TourAPI 호출 + DB 캐시(TOUR_CACHE_ENTRY) 관리를 전담하는 서비스.
 *
 * 공공 API는 보통 일일 호출 한도가 있어서, 사용자 요청이 들어올 때마다 매번 TourAPI를 직접
 * 부르면 트래픽이 늘었을 때 한도를 금방 소진할 수 있다. 그래서 여기서는:
 *   1) TourCacheRefreshScheduler가 매일 오전 9시(+ 필요할 때만 앱 기동 시 1번, isWarmupNeeded()
 *      참고)에 refreshAll()을 호출해서 실제 TourAPI를 딱 10번(festivals 1 + destinations 5 +
 *      industry 4)만 호출하고 DB(TOUR_CACHE_ENTRY 테이블)에 저장해둔다.
 *   2) 사용자 요청을 처리하는 TourService는 getCached()로 이 DB 캐시만 읽는다 — 트래픽이
 *      아무리 늘어도 TourAPI 호출 횟수는 하루 10번 그대로다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourCacheService {

  // TourService가 festivals 전체 목록을 읽을 때 쓰는 캐시 키 (getFestivals에서 소분류 필터링을 함께 함)
  public static final String FESTIVALS_ALL = "festivals:ALL";

  // refreshAll()이 채우는 캐시 키 총 개수 (festivals 1 + destinations 5 + industry 4)
  private static final int EXPECTED_CACHE_KEY_COUNT = 10;

  // 캐시가 이 시간 안에 갱신된 적이 있으면, 재시작하더라도 웜업을 건너뛴다.
  // (개발 중 재배포/재시작을 자주 할 때마다 TourAPI를 다시 호출하는 걸 막기 위함)
  private static final Duration STARTUP_SKIP_WINDOW = Duration.ofMinutes(30);

  private static final String CONTENT_TYPE_TOUR_SPOT = "12";
  private static final String CONTENT_TYPE_CULTURE_FACILITY = "14";
  private static final String CONTENT_TYPE_LEPORTS = "28";
  private static final String CONTENT_TYPE_ACCOMMODATION = "32";
  private static final String CONTENT_TYPE_SHOPPING = "38";
  private static final String CONTENT_TYPE_RESTAURANT = "39";

  private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

  private static final String[] FOOD_KEYWORDS = {
      "음식", "푸드", "food", "맛", "미식", "술", "주류", "막걸리", "와인", "커피", "차", "디저트", "빵", "김치", "떡"
  };
  private static final String[] EXPERIENCE_KEYWORDS = {"체험", "만들기", "캠프", "탐험", "워크숍"};

  private final TourApiClient tourApiClient;
  private final TourCategoryService tourCategoryService;
  private final TourCacheEntryRepository cacheEntryRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 사용자 요청 처리 경로 — DB 캐시만 읽는다. 캐시가 아직 없으면(최초 기동 직후 등) 빈 목록. */
  public List<TourItemResponse> getCached(String cacheKey) {
    return cacheEntryRepository.findByCacheKey(cacheKey)
        .map(entry -> deserialize(entry.getPayloadJson()))
        .orElseGet(() -> {
          log.warn("TourAPI 캐시가 아직 없습니다 (cacheKey={}). 다음 새로고침 전까지는 빈 목록을 반환합니다.", cacheKey);
          return List.of();
        });
  }

  /**
   * 앱 기동 시 웜업이 실제로 필요한지 판단한다.
   * - 캐시 10개 중 하나라도 비어있으면(최초 기동, 일부 갱신 실패 등) true
   * - 전부 있어도 가장 오래된 캐시가 STARTUP_SKIP_WINDOW(30분)보다 오래됐으면 true
   * - 방금 전에 이미 갱신됐다면(짧은 간격의 재시작 반복 등) false -> 웜업 생략
   */
  public boolean isWarmupNeeded() {
    List<TourCacheEntry> entries = cacheEntryRepository.findAll();
    if (entries.size() < EXPECTED_CACHE_KEY_COUNT) {
      return true;
    }
    LocalDateTime oldestFetchedAt = entries.stream()
        .map(TourCacheEntry::getFetchedAt)
        .min(Comparator.naturalOrder())
        .orElse(null);
    return oldestFetchedAt == null || oldestFetchedAt.isBefore(LocalDateTime.now().minus(STARTUP_SKIP_WINDOW));
  }

  /** 스케줄러 전용 — 실제로 TourAPI를 호출해서 캐시 테이블을 통째로 갱신한다. */
  public void refreshAll() {
    log.info("TourAPI 캐시 새로고침 시작");

    safeRefresh(FESTIVALS_ALL, this::fetchFestivalsFromApi);

    safeRefresh("destinations:전체", () -> fetchAreaBasedList(CONTENT_TYPE_TOUR_SPOT, null, null, "전체", 40));
    safeRefresh("destinations:체험 관광", () -> fetchAreaBasedList(
        CONTENT_TYPE_TOUR_SPOT, "A02", tourCategoryService.resolveCat2("A02", "체험").orElse(null), "체험 관광", 40));
    safeRefresh("destinations:역사 관광", () -> fetchAreaBasedList(
        CONTENT_TYPE_TOUR_SPOT, "A02", tourCategoryService.resolveCat2("A02", "역사").orElse(null), "역사 관광", 40));
    safeRefresh("destinations:자연 관광", () -> fetchAreaBasedList(CONTENT_TYPE_TOUR_SPOT, "A01", null, "자연 관광", 40));
    safeRefresh("destinations:문화 관광", () -> fetchAreaBasedList(CONTENT_TYPE_CULTURE_FACILITY, null, null, "문화 관광", 40));

    safeRefresh("industry:숙박", () -> fetchAreaBasedList(CONTENT_TYPE_ACCOMMODATION, null, null, "숙박", 40));
    safeRefresh("industry:음식", () -> fetchAreaBasedList(CONTENT_TYPE_RESTAURANT, null, null, "음식", 40));
    safeRefresh("industry:레저 스포츠", () -> fetchAreaBasedList(CONTENT_TYPE_LEPORTS, null, null, "레저 스포츠", 40));
    safeRefresh("industry:쇼핑", () -> fetchAreaBasedList(CONTENT_TYPE_SHOPPING, null, null, "쇼핑", 40));

    log.info("TourAPI 캐시 새로고침 완료");
  }

  // 물리 조회 10개 중 하나가 실패해도(TourAPI 일시 장애/타임아웃 등) 나머지는 정상적으로 갱신되도록
  // 개별적으로 감싼다. 실패한 키는 이전에 캐시된 값을 그대로 유지한다(완전히 비어버리지 않도록).
  private void safeRefresh(String cacheKey, Supplier<List<TourItemResponse>> fetcher) {
    try {
      List<TourItemResponse> items = fetcher.get();
      upsert(cacheKey, items);
      log.info("TourAPI 캐시 갱신 성공 (cacheKey={}, count={})", cacheKey, items.size());
    } catch (Exception e) {
      log.warn("TourAPI 캐시 갱신 실패 - 이전 캐시를 그대로 유지합니다 (cacheKey={})", cacheKey, e);
    }
  }

  private void upsert(String cacheKey, List<TourItemResponse> items) {
    String json = serialize(items);
    LocalDateTime now = LocalDateTime.now();
    TourCacheEntry entry = cacheEntryRepository.findByCacheKey(cacheKey)
        .orElseGet(() -> TourCacheEntry.builder().cacheKey(cacheKey).payloadJson(json).fetchedAt(now).build());
    entry.refresh(json, now);
    cacheEntryRepository.save(entry);
  }

  private String serialize(List<TourItemResponse> items) {
    try {
      return objectMapper.writeValueAsString(items);
    } catch (Exception e) {
      throw new IllegalStateException("TourAPI 캐시 직렬화 실패", e);
    }
  }

  private List<TourItemResponse> deserialize(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<TourItemResponse>>() {});
    } catch (Exception e) {
      log.error("TourAPI 캐시 역직렬화 실패", e);
      return List.of();
    }
  }

  // ------------------------------------------------------------------
  // 아래부터는 실제 TourAPI(외부) 호출 로직. refreshAll()에서만 호출된다.
  // ------------------------------------------------------------------

  private List<TourItemResponse> fetchFestivalsFromApi() {
    String today = LocalDate.now().format(YYYYMMDD);

    Map<String, String> params = new LinkedHashMap<>();
    params.put("numOfRows", "60");
    params.put("pageNo", "1");
    params.put("arrange", "A");
    params.put("eventStartDate", today); // 오늘 이후 종료되는(=진행중+예정) 축제만 조회

    JsonNode items = tourApiClient.callList("searchFestival2", params);
    return StreamSupport.stream(items.spliterator(), false)
        .map(this::mapFestivalItem)
        .collect(Collectors.toList());
  }

  private List<TourItemResponse> fetchAreaBasedList(
      String contentTypeId, String cat1, String cat2, String categoryLabel, int numOfRows
  ) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("numOfRows", String.valueOf(numOfRows));
    params.put("pageNo", "1");
    params.put("arrange", "O"); // 대표이미지 있는 항목 우선 정렬 (카드형 UI에 유리)
    if (contentTypeId != null) params.put("contentTypeId", contentTypeId);
    if (cat1 != null) params.put("cat1", cat1);
    if (cat2 != null) params.put("cat2", cat2);

    JsonNode items = tourApiClient.callList("areaBasedList2", params);
    return StreamSupport.stream(items.spliterator(), false)
        .map(item -> mapBasicItem(item, categoryLabel))
        .collect(Collectors.toList());
  }

  private TourItemResponse mapBasicItem(JsonNode item, String categoryLabel) {
    return TourItemResponse.builder()
        .contentId(item.path("contentid").asText(null))
        .contentTypeId(item.path("contenttypeid").asText(null))
        .title(item.path("title").asText(""))
        .category(categoryLabel)
        .address(joinAddress(item))
        .imageUrl(blankToNull(item.path("firstimage").asText(null)))
        .mapX(numericOrNull(item, "mapx"))
        .mapY(numericOrNull(item, "mapy"))
        .build();
  }

  private TourItemResponse mapFestivalItem(JsonNode item) {
    String start = blankToNull(item.path("eventstartdate").asText(null));
    String end = blankToNull(item.path("eventenddate").asText(null));
    String title = item.path("title").asText("");

    return TourItemResponse.builder()
        .contentId(item.path("contentid").asText(null))
        .contentTypeId(item.path("contenttypeid").asText(null))
        .title(title)
        .category(classifyFestivalTitle(title))
        .address(joinAddress(item))
        .imageUrl(blankToNull(item.path("firstimage").asText(null)))
        .mapX(numericOrNull(item, "mapx"))
        .mapY(numericOrNull(item, "mapy"))
        .eventStartDate(start)
        .eventEndDate(end)
        .status(computeFestivalStatus(start, end))
        .build();
  }

  private Double numericOrNull(JsonNode item, String field) {
    String text = item.path(field).asText("");
    return text.isBlank() ? null : item.path(field).asDouble();
  }

  private String computeFestivalStatus(String start, String end) {
    try {
      LocalDate today = LocalDate.now();
      LocalDate startDate = start == null ? null : LocalDate.parse(start, YYYYMMDD);
      LocalDate endDate = end == null ? null : LocalDate.parse(end, YYYYMMDD);
      boolean started = startDate != null && !today.isBefore(startDate);
      boolean notEnded = endDate == null || !today.isAfter(endDate);
      return (started && notEnded) ? "ongoing" : "upcoming";
    } catch (Exception e) {
      return "upcoming";
    }
  }

  // TourAPI가 축제를 "문화 예술 / 체험 / 음식 음료"로 나눠주지 않아서, 제목 키워드로 최선의 추정을 한다.
  private String classifyFestivalTitle(String title) {
    for (String keyword : FOOD_KEYWORDS) {
      if (title.contains(keyword)) return "음식 음료";
    }
    for (String keyword : EXPERIENCE_KEYWORDS) {
      if (title.contains(keyword)) return "체험";
    }
    return "문화 예술";
  }

  private String joinAddress(JsonNode item) {
    String addr1 = item.path("addr1").asText("");
    String addr2 = item.path("addr2").asText("");
    String full = (addr1 + " " + addr2).trim();
    return full.isEmpty() ? null : full;
  }

  private String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
