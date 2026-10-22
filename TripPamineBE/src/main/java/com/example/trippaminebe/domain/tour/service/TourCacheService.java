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

import com.example.trippaminebe.common.cache.DailyCacheRefreshPolicy;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * TourAPI 호출 + DB 캐시(TOUR_CACHE_ENTRY) 관리를 전담하는 서비스.
 *
 * 공공 API는 보통 일일 호출 한도가 있어서, 사용자 요청이 들어올 때마다 매번 TourAPI를 직접
 * 부르면 트래픽이 늘었을 때 한도를 금방 소진할 수 있다. 그래서 여기서는:
 *   1) TourCacheRefreshScheduler가 매일 한국시간 오전 9시에 10개 키를 갱신한다.
 *      서버가 정기 갱신을 놓쳤을 때는 기동 시 보완하고, 일부 키만 없으면 그 키만 갱신한다.
 *   2) 사용자 목록 조회는 Oracle 캐시만 읽으므로 요청 증가가 곧 외부 API 호출 증가는 아니다.
 *   ※ 외부 API 장애, 서버 다중 실행 시 호출 횟수는 스케줄 횟수와 다를 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourCacheService {

  // TourService가 festivals 전체 목록을 읽을 때 쓰는 캐시 키 (getFestivals에서 소분류 필터링을 함께 함)
  public static final String FESTIVALS_ALL = "festivals:ALL";

  // findByContentId()가 상세 페이지 진입 시 뒤질 캐시 키 전체 목록 (refreshAll()이 채우는 키와 동일)
  private static final String[] ALL_CACHE_KEYS = {
      FESTIVALS_ALL,
      "destinations:전체", "destinations:체험 관광", "destinations:역사 관광", "destinations:자연 관광", "destinations:문화 관광",
      "industry:숙박", "industry:음식", "industry:레저 스포츠", "industry:쇼핑"
  };

  // @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")와 같은 기준으로 판단한다.
  private static final LocalTime DAILY_REFRESH_AT = LocalTime.of(9, 0);
  private static final ZoneId REFRESH_ZONE = ZoneId.of("Asia/Seoul");

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

  // 10개 관광 JSON 캐시를 60초간 재사용하여 매 요청의 원격 Oracle 조회를 줄입니다.
  // 다중 서버의 외부 갱신도 최대 약 60초 후 다시 읽으며, 이 서비스의 성공 갱신은 즉시 반영합니다.
  private static final long MEMORY_CACHE_TTL_NANOS = java.time.Duration.ofSeconds(60).toNanos();
  private final Map<String, CachedPayload> memoryCache = new ConcurrentHashMap<>();

  private record CachedPayload(String json, long loadedAtNanos) {
    boolean isFresh() {
      return System.nanoTime() - loadedAtNanos < MEMORY_CACHE_TTL_NANOS;
    }
  }

  /**
   * 사용자 요청 경로: 메모리 캐시 적중 시 Oracle 조회를 생략합니다.
   * 메모리에는 JSON만 보관하고 DTO는 매 요청 새로 역직렬화해 수정 가능한 객체를 공유하지 않습니다.
   */
  public List<TourItemResponse> getCached(String cacheKey) {
    long begin = System.nanoTime();
    CachedPayload cached = memoryCache.get(cacheKey);
    boolean hit = cached != null && cached.isFresh();
    long[] dbNanos = {0L};
    if (!hit) {
      // 같은 키의 동시 요청은 한 요청만 Oracle을 읽게 하여 캐시 미스 폭주를 방지합니다.
      cached = memoryCache.compute(cacheKey, (key, current) -> {
        if (current != null && current.isFresh()) {
          return current;
        }
        long dbStart = System.nanoTime();
        try {
          return cacheEntryRepository.findByCacheKey(key)
              .map(entry -> new CachedPayload(entry.getPayloadJson(), System.nanoTime()))
              .orElse(null);
        } finally {
          dbNanos[0] = System.nanoTime() - dbStart;
        }
      });
    }
    long afterLookup = System.nanoTime();

    List<TourItemResponse> result;
    if (cached == null) {
      log.warn("TourAPI 캐시가 아직 없습니다 (cacheKey={}). 다음 새로고침 전까지는 빈 목록을 반환합니다.", cacheKey);
      result = List.of();
    } else {
      result = deserialize(cached.json());
    }

    long completed = System.nanoTime();
    log.debug("[TourCachePerf] key={} source={} db_ms={} lookup_ms={} deserialize_ms={} total_ms={}",
        cacheKey, hit ? "memory" : dbNanos[0] > 0 ? "oracle" : "memory_after_wait",
        dbNanos[0] / 1_000_000.0,
        (afterLookup - begin) / 1_000_000.0,
        (completed - afterLookup) / 1_000_000.0,
        (completed - begin) / 1_000_000.0);
    return result;
  }

  /** 기존 관광산업 전체 조회의 키 순서와 데이터 중복 항목은 그대로 유지합니다. */
  public List<TourItemResponse> getCachedMany(List<String> cacheKeys) {
    if (cacheKeys == null || cacheKeys.isEmpty()) {
      return List.of();
    }

    Map<String, String> payloads = findCachedPayloads(cacheKeys);
    List<TourItemResponse> merged = new ArrayList<>();
    for (String cacheKey : cacheKeys) {
      String payload = payloads.get(cacheKey);
      if (payload == null) {
        log.warn("TourAPI 캐시가 아직 없습니다 (cacheKey={}).", cacheKey);
        continue;
      }
      merged.addAll(deserialize(payload));
    }
    return merged;
  }

  // 미스가 발생한 키만 IN 절로 한 번에 읽고 캐시에 적재합니다.
  // putIfAbsent 대신 compute를 사용해 정기 갱신 중인 키에 오래된 조회값을 덮어쓰지 않습니다.
  private Map<String, String> findCachedPayloads(List<String> cacheKeys) {
    Map<String, String> payloads = new HashMap<>();
    List<String> missedKeys = new ArrayList<>();
    for (String key : cacheKeys) {
      CachedPayload cached = memoryCache.get(key);
      if (cached != null && cached.isFresh()) {
        payloads.put(key, cached.json());
      } else if (!missedKeys.contains(key)) {
        missedKeys.add(key);
      }
    }
    if (missedKeys.isEmpty()) {
      return payloads;
    }

    Map<String, String> loaded = cacheEntryRepository.findByCacheKeyIn(missedKeys).stream()
        .collect(Collectors.toMap(TourCacheEntry::getCacheKey, TourCacheEntry::getPayloadJson));
    for (String key : missedKeys) {
      String json = loaded.get(key);
      if (json == null) {
        continue;
      }
      CachedPayload result = memoryCache.compute(key, (k, current) ->
          current != null && current.isFresh()
              ? current : new CachedPayload(json, System.nanoTime()));
      payloads.put(key, result.json());
    }
    return payloads;
  }

  /** 관광 상세에서 목록의 기본 정보를 찾되, 조회한 키 10개를 다른 페이지와 재사용합니다. */
  public Optional<TourItemResponse> findByContentId(String contentId) {
    if (contentId == null || contentId.isBlank()) {
      return Optional.empty();
    }
    Map<String, String> payloads = findCachedPayloads(List.of(ALL_CACHE_KEYS));
    for (String cacheKey : ALL_CACHE_KEYS) {
      String payload = payloads.get(cacheKey);
      if (payload == null) {
        continue;
      }
      Optional<TourItemResponse> match = deserialize(payload).stream()
          .filter(item -> contentId.equals(item.getContentId()))
          .findFirst();
      if (match.isPresent()) {
        return match;
      }
    }
    return Optional.empty();
  }

  /**
   * 기동 시 정기 갱신 누락 여부를 확인한다.
   * - 모든 키가 없거나 마지막 정기 갱신을 놓쳤으면 전체 10개 갱신
   * - 일부 키만 없고 나머지는 이번 정기 주기에 갱신됐다면 누락된 키만 갱신
   * - 성공 기록이 하나라도 있으면 나머지 오래된 키는 다음 정기 갱신까지 유지한다.
   *   (부분 실패 후 재기동할 때마다 외부 API 전체 재호출하는 현상 방지)
   */
  public void refreshOnStartup() {
    Map<String, LocalDateTime> fetchedAtByKey = new HashMap<>();
    for (TourCacheEntry entry : cacheEntryRepository.findAll()) {
      fetchedAtByKey.put(entry.getCacheKey(), entry.getFetchedAt());
    }
    List<String> keys = DailyCacheRefreshPolicy.selectStartupKeys(
        List.of(ALL_CACHE_KEYS), fetchedAtByKey, DAILY_REFRESH_AT, REFRESH_ZONE,
        ZoneId.systemDefault(), Instant.now());
    if (keys.isEmpty()) {
      log.info("TourAPI 정기 갱신 기록이 있어 기동 시 재호출을 생략합니다.");
      return;
    }
    log.info("TourAPI 기동 시 보완 갱신: {}개 키", keys.size());
    refreshSelected(new HashSet<>(keys));
  }

  /** 매일 정기 갱신은 기존처럼 10개 캐시를 모두 업데이트한다. */
  public void refreshAll() {
    log.info("TourAPI 캐시 정기 갱신 시작");
    refreshSelected(new HashSet<>(List.of(ALL_CACHE_KEYS)));
    log.info("TourAPI 캐시 정기 갱신 완료");
  }

  // 한 곳에서만 키 → API 호출을 정의해 전체/선택 갱신 로직의 중복을 방지한다.
  private void refreshSelected(Set<String> selectedKeys) {
    if (selectedKeys.contains(FESTIVALS_ALL)) safeRefresh(FESTIVALS_ALL, this::fetchFestivalsFromApi);

    if (selectedKeys.contains("destinations:전체")) safeRefresh("destinations:전체", () -> fetchAreaBasedList(CONTENT_TYPE_TOUR_SPOT, null, null, "전체", 40));
    if (selectedKeys.contains("destinations:체험 관광")) safeRefresh("destinations:체험 관광", () -> fetchAreaBasedList(
        CONTENT_TYPE_TOUR_SPOT, "A02", tourCategoryService.resolveCat2("A02", "체험").orElse(null), "체험 관광", 40));
    if (selectedKeys.contains("destinations:역사 관광")) safeRefresh("destinations:역사 관광", () -> fetchAreaBasedList(
        CONTENT_TYPE_TOUR_SPOT, "A02", tourCategoryService.resolveCat2("A02", "역사").orElse(null), "역사 관광", 40));
    if (selectedKeys.contains("destinations:자연 관광")) safeRefresh("destinations:자연 관광", () -> fetchAreaBasedList(CONTENT_TYPE_TOUR_SPOT, "A01", null, "자연 관광", 40));
    if (selectedKeys.contains("destinations:문화 관광")) safeRefresh("destinations:문화 관광", () -> fetchAreaBasedList(CONTENT_TYPE_CULTURE_FACILITY, null, null, "문화 관광", 40));

    if (selectedKeys.contains("industry:숙박")) safeRefresh("industry:숙박", () -> fetchAreaBasedList(CONTENT_TYPE_ACCOMMODATION, null, null, "숙박", 40));
    if (selectedKeys.contains("industry:음식")) safeRefresh("industry:음식", () -> fetchAreaBasedList(CONTENT_TYPE_RESTAURANT, null, null, "음식", 40));
    if (selectedKeys.contains("industry:레저 스포츠")) safeRefresh("industry:레저 스포츠", () -> fetchAreaBasedList(CONTENT_TYPE_LEPORTS, null, null, "레저 스포츠", 40));
    if (selectedKeys.contains("industry:쇼핑")) safeRefresh("industry:쇼핑", () -> fetchAreaBasedList(CONTENT_TYPE_SHOPPING, null, null, "쇼핑", 40));
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
    // Oracle 저장이 성공한 뒤에만 새 JSON을 게시합니다. 실패 시 기존 메모리 캐시를 유지합니다.
    memoryCache.put(cacheKey, new CachedPayload(json, System.nanoTime()));
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
  // 아래부터는 외부 TourAPI 호출 로직. 정기 갱신 또는 기동 보완 갱신에서만 호출한다.
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



