package com.example.trippaminebe.domain.finlife.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 금융감독원 오픈API(금융상품 한눈에, FINLIFE) 호출 전담 클라이언트.
 */
@Slf4j
@Component
public class FinlifeApiClient {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${finlife.api.base-url}")
  private String baseUrl;

  @Value("${finlife.api.auth-key}")
  private String authKey;

  public FinlifeApiClient(RestTemplate finlifeApiRestTemplate) {
    this.restTemplate = finlifeApiRestTemplate;
  }

  public JsonNode call(String operation, String topFinGrpNo) {
    URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/" + operation)
        .queryParam("auth", authKey)
        .queryParam("topFinGrpNo", topFinGrpNo)
        .queryParam("pageNo", "1")
        .build(true)
        .toUri();

    try {
      String rawResponse = restTemplate.getForObject(uri, String.class);
      JsonNode root = objectMapper.readTree(rawResponse);
      JsonNode result = root.path("result");

      String errCd = result.path("err_cd").asText("");
      if (!errCd.isEmpty() && !"000".equals(errCd)) {
        String errMsg = result.path("err_msg").asText("알 수 없는 오류");
        throw new FinlifeApiException(
            "금융상품 API 호출 실패(" + operation + ", topFinGrpNo=" + topFinGrpNo + "): " + errMsg);
      }
      return result;
    } catch (FinlifeApiException e) {
      throw e;
    } catch (Exception e) {
      log.error("금융상품 API 호출 중 오류 발생 (operation={}, topFinGrpNo={})", operation, topFinGrpNo, e);
      throw new FinlifeApiException("금융상품 API 호출 중 오류가 발생했습니다: " + e.getMessage(), e);
    }
  }
}
