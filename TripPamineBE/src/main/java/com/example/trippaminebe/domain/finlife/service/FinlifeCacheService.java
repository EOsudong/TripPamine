package com.example.trippaminebe.domain.finlife.service;

import com.example.trippaminebe.domain.finlife.client.FinlifeApiClient;
import com.example.trippaminebe.domain.finlife.dto.FinancialProductOptionResponse;
import com.example.trippaminebe.domain.finlife.dto.FinancialProductResponse;
import com.example.trippaminebe.domain.finlife.entity.FinlifeCacheEntry;
import com.example.trippaminebe.domain.finlife.repository.FinlifeCacheEntryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 금융감독원 오픈API(금융상품 한눈에, FINLIFE) 호출 + DB 캐시(FINLIFE_CACHE_ENTRY) 관리를 전담하는 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinlifeCacheService {

  // 금융감독원 FINLIFE API의 업권코드. 예금/적금 상품이 존재하는 업권은 이 둘뿐이다.
  public static final String TOP_FIN_GRP_BANK = "020000";          // 은행
  public static final String TOP_FIN_GRP_SAVINGS_BANK = "030200";  // 저축은행

  private static final String DEPOSIT_OPERATION = "depositProductsSearch.json";
  private static final String SAVING_OPERATION = "savingProductsSearch.json";

  // refreshAll()이 채우는 캐시 키 총 개수 (예금 2업권 + 적금 2업권)
  private static final int EXPECTED_CACHE_KEY_COUNT = 4;

  private static final String[] ALL_CACHE_KEYS = {
      cacheKey("deposit", TOP_FIN_GRP_BANK), cacheKey("deposit", TOP_FIN_GRP_SAVINGS_BANK),
      cacheKey("saving", TOP_FIN_GRP_BANK), cacheKey("saving", TOP_FIN_GRP_SAVINGS_BANK),
  };

  // 캐시가 이 시간 안에 갱신된 적이 있으면, 재시작하더라도 웜업을 건너뛴다.
  // (개발 중 재배포/재시작을 자주 할 때마다 FINLIFE API를 다시 호출하는 걸 막기 위함)
  private static final Duration STARTUP_SKIP_WINDOW = Duration.ofMinutes(30);

  private final FinlifeApiClient finlifeApiClient;
  private final FinlifeCacheEntryRepository cacheEntryRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public static String cacheKey(String productType, String topFinGrpNo) {
    return productType + ":" + topFinGrpNo;
  }

  /** 사용자 요청 처리 경로 — DB 캐시만 읽는다. 캐시가 아직 없으면(최초 기동 직후 등) 빈 목록. */
  public List<FinancialProductResponse> getCached(String cacheKey) {
    return cacheEntryRepository.findByCacheKey(cacheKey)
        .map(entry -> deserialize(entry.getPayloadJson()))
        .orElseGet(() -> {
          log.warn("FINLIFE 캐시가 아직 없습니다 (cacheKey={}). 다음 새로고침 전까지는 빈 목록을 반환합니다.", cacheKey);
          return List.of();
        });
  }

  /**
   * 앱 기동 시 웜업이 실제로 필요한지 판단한다.
   * - 캐시 4개 중 하나라도 비어있으면(최초 기동, 일부 갱신 실패 등) true
   * - 전부 있어도 가장 오래된 캐시가 STARTUP_SKIP_WINDOW(30분)보다 오래됐으면 true
   * - 방금 전에 이미 갱신됐다면(짧은 간격의 재시작 반복 등) false -> 웜업 생략
   */
  public boolean isWarmupNeeded() {
    List<FinlifeCacheEntry> entries = cacheEntryRepository.findAll();
    if (entries.size() < EXPECTED_CACHE_KEY_COUNT) {
      return true;
    }
    LocalDateTime oldestFetchedAt = entries.stream()
        .map(FinlifeCacheEntry::getFetchedAt)
        .min(Comparator.naturalOrder())
        .orElse(null);
    return oldestFetchedAt == null || oldestFetchedAt.isBefore(LocalDateTime.now().minus(STARTUP_SKIP_WINDOW));
  }

  /** 스케줄러 전용 — 실제로 FINLIFE API를 호출해서 캐시 테이블을 통째로 갱신한다. */
  public void refreshAll() {
    log.info("FINLIFE(금융상품 한눈에) 캐시 새로고침 시작");

    safeRefresh(cacheKey("deposit", TOP_FIN_GRP_BANK), () -> fetchProducts(DEPOSIT_OPERATION, TOP_FIN_GRP_BANK, "deposit"));
    safeRefresh(cacheKey("deposit", TOP_FIN_GRP_SAVINGS_BANK), () -> fetchProducts(DEPOSIT_OPERATION, TOP_FIN_GRP_SAVINGS_BANK, "deposit"));
    safeRefresh(cacheKey("saving", TOP_FIN_GRP_BANK), () -> fetchProducts(SAVING_OPERATION, TOP_FIN_GRP_BANK, "saving"));
    safeRefresh(cacheKey("saving", TOP_FIN_GRP_SAVINGS_BANK), () -> fetchProducts(SAVING_OPERATION, TOP_FIN_GRP_SAVINGS_BANK, "saving"));

    log.info("FINLIFE(금융상품 한눈에) 캐시 새로고침 완료");
  }

  // 물리 조회 4개 중 하나가 실패해도(FINLIFE API 일시 장애/타임아웃/인증키 오류 등) 나머지는 정상적으로
  // 갱신되도록 개별적으로 감싼다. 실패한 키는 이전에 캐시된 값을 그대로 유지한다(완전히 비어버리지 않도록).
  private void safeRefresh(String cacheKey, Supplier<List<FinancialProductResponse>> fetcher) {
    try {
      List<FinancialProductResponse> items = fetcher.get();
      upsert(cacheKey, items);
      log.info("FINLIFE 캐시 갱신 성공 (cacheKey={}, count={})", cacheKey, items.size());
    } catch (Exception e) {
      log.warn("FINLIFE 캐시 갱신 실패 - 이전 캐시를 그대로 유지합니다 (cacheKey={})", cacheKey, e);
    }
  }

  private void upsert(String cacheKey, List<FinancialProductResponse> items) {
    String json = serialize(items);
    LocalDateTime now = LocalDateTime.now();
    FinlifeCacheEntry entry = cacheEntryRepository.findByCacheKey(cacheKey)
        .orElseGet(() -> FinlifeCacheEntry.builder().cacheKey(cacheKey).payloadJson(json).fetchedAt(now).build());
    entry.refresh(json, now);
    cacheEntryRepository.save(entry);
  }

  private String serialize(List<FinancialProductResponse> items) {
    try {
      return objectMapper.writeValueAsString(items);
    } catch (Exception e) {
      throw new IllegalStateException("FINLIFE 캐시 직렬화 실패", e);
    }
  }

  private List<FinancialProductResponse> deserialize(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<FinancialProductResponse>>() {});
    } catch (Exception e) {
      log.error("FINLIFE 캐시 역직렬화 실패", e);
      return List.of();
    }
  }

  /**
   * depositProductsSearch.json / savingProductsSearch.json 응답의 baseList(상품 기본정보)와
   * optionList(기간별 금리)를 (finCoNo, finPrdtCd) 키로 묶어서 상품 하나당 옵션 목록을 갖는
   * FinancialProductResponse 리스트로 합친다.
   */
  private List<FinancialProductResponse> fetchProducts(String operation, String topFinGrpNo, String productType) {
    JsonNode result = finlifeApiClient.call(operation, topFinGrpNo);
    JsonNode baseList = result.path("baseList");
    JsonNode optionList = result.path("optionList");

    // key: finCoNo + "|" + finPrdtCd
    Map<String, List<FinancialProductOptionResponse>> optionsByProduct = new LinkedHashMap<>();
    StreamSupport.stream(optionList.spliterator(), false).forEach(option -> {
      String key = productKey(option);
      optionsByProduct.computeIfAbsent(key, k -> new ArrayList<>()).add(mapOption(option));
    });

    return StreamSupport.stream(baseList.spliterator(), false)
        .map(base -> mapProduct(base, productType, optionsByProduct.getOrDefault(productKey(base), List.of())))
        .collect(Collectors.toList());
  }

  private String productKey(JsonNode node) {
    return node.path("fin_co_no").asText("") + "|" + node.path("fin_prdt_cd").asText("");
  }

  private FinancialProductOptionResponse mapOption(JsonNode option) {
    return FinancialProductOptionResponse.builder()
        .saveTrm(blankToNull(option.path("save_trm").asText(null)))
        .intrRateTypeNm(blankToNull(option.path("intr_rate_type_nm").asText(null)))
        .intrRate(numericOrNull(option, "intr_rate"))
        .intrRate2(numericOrNull(option, "intr_rate2"))
        .rsrvTypeNm(blankToNull(option.path("rsrv_type_nm").asText(null))) // 적금 응답에만 존재, 예금은 항상 null
        .build();
  }

  private FinancialProductResponse mapProduct(
      JsonNode base, String productType, List<FinancialProductOptionResponse> options
  ) {
    // 화면에 대표 금리(카드 배지, 정렬 기준)로 보여줄 값 - 옵션들의 "최고우대금리" 중 최댓값
    Double maxRate = options.stream()
        .map(FinancialProductOptionResponse::getIntrRate2)
        .filter(rate -> rate != null)
        .max(Comparator.naturalOrder())
        .orElse(null);

    return FinancialProductResponse.builder()
        .finCoNo(base.path("fin_co_no").asText(null))
        .korCoNm(base.path("kor_co_nm").asText(""))
        .finPrdtCd(base.path("fin_prdt_cd").asText(null))
        .finPrdtNm(base.path("fin_prdt_nm").asText(""))
        .productType(productType)
        .joinWay(blankToNull(base.path("join_way").asText(null)))
        .joinMember(blankToNull(base.path("join_member").asText(null)))
        .joinDenyLabel(joinDenyLabel(base.path("join_deny").asText(null)))
        .spclCnd(blankToNull(base.path("spcl_cnd").asText(null)))
        .mtrtInt(blankToNull(base.path("mtrt_int").asText(null)))
        .etcNote(blankToNull(base.path("etc_note").asText(null)))
        .dclsMonth(blankToNull(base.path("dcls_month").asText(null)))
        .maxRate(maxRate)
        .options(options)
        .build();
  }

  // FINLIFE API의 join_deny 코드: "1" 제한없음, "2" 서민전용, "3" 일부제한
  private String joinDenyLabel(String joinDeny) {
    if (joinDeny == null) return null;
    return switch (joinDeny) {
      case "1" -> "제한없음";
      case "2" -> "서민전용";
      case "3" -> "일부제한";
      default -> null;
    };
  }

  private Double numericOrNull(JsonNode item, String field) {
    JsonNode node = item.path(field);
    if (node.isMissingNode() || node.isNull()) return null;
    String text = node.asText("");
    return text.isBlank() ? null : node.asDouble();
  }

  private String blankToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }
}
