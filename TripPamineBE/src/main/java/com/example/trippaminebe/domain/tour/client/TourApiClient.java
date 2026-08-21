package com.example.trippaminebe.domain.tour.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 한국관광공사 오픈API(한국관광콘텐츠랩, TourAPI KorService2) 호출 전담 클라이언트.
 */
@Slf4j
@Component
public class TourApiClient {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${tour.api.base-url}")
  private String baseUrl;

  @Value("${tour.api.service-key}")
  private String serviceKey;

  @Value("${tour.api.mobile-app:TripPamine}")
  private String mobileApp;

  @Value("${tour.api.mobile-os:ETC}")
  private String mobileOs;

  public TourApiClient(RestTemplate tourApiRestTemplate) {
    this.restTemplate = tourApiRestTemplate;
  }

  /**
   * operation(예: areaBasedList2, searchFestival2, categoryCode2)을 호출해서
   * response.body.items.item 배열을 그대로 돌려준다. 결과가 없으면 빈 배열.
   */
  public JsonNode callList(String operation, Map<String, String> extraParams) {
    JsonNode root = call(operation, extraParams);
    JsonNode items = root.path("response").path("body").path("items").path("item");

    if (items.isMissingNode() || items.isNull()) {
      return objectMapper.createArrayNode();
    }
    if (items.isArray()) {
      return items;
    }
    // 결과가 1건이면 배열이 아니라 단일 객체로 내려오는 TourAPI 특성 -> 배열로 감싸서 정규화
    return objectMapper.createArrayNode().add(items);
  }

  private JsonNode call(String operation, Map<String, String> extraParams) {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("MobileOS", mobileOs);
    params.put("MobileApp", mobileApp);
    params.put("_type", "json");
    params.putAll(extraParams);

    UriComponentsBuilder builder = UriComponentsBuilder
        .fromUriString(baseUrl + "/" + operation)
        .queryParam("serviceKey", encodeServiceKeyIfNeeded(serviceKey));
    params.forEach(builder::queryParam);

    // serviceKey는 위에서 이미 (필요한 경우에만) 인코딩을 마쳤고, 나머지 파라미터는 전부
    // 영문/숫자로만 이루어진 값(operation, MobileOS 등)이라 추가 인코딩이 필요 없다.
    // build(true)로 "이미 인코딩된 상태"임을 명시해서 serviceKey가 다시 인코딩되는 것을 막는다.
    URI uri = builder.build(true).toUri();

    try {
      String rawResponse = restTemplate.getForObject(uri, String.class);
      JsonNode root = objectMapper.readTree(rawResponse);

      String resultCode = root.path("response").path("header").path("resultCode").asText("");
      if (!resultCode.isEmpty() && !"0000".equals(resultCode)) {
        String resultMsg = root.path("response").path("header").path("resultMsg").asText("알 수 없는 오류");
        throw new TourApiException("TourAPI 호출 실패(" + operation + "): " + resultMsg);
      }
      return root;
    } catch (TourApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("TourAPI 호출 중 오류 발생 (operation={})", operation, e);
      throw new TourApiException("TourAPI 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
    }
  }

  // 키에 "%2B", "%2F", "%3D" 같은 퍼센트 인코딩 흔적이 있으면 이미 인코딩된 키로 보고 그대로 쓰고,
  // 없으면(디코딩 원본 키) 한 번 인코딩해서 쓴다.
  private String encodeServiceKeyIfNeeded(String key) {
    if (key != null && key.matches(".*%[0-9A-Fa-f]{2}.*")) {
      return key;
    }
    return URLEncoder.encode(key, StandardCharsets.UTF_8);
  }
}