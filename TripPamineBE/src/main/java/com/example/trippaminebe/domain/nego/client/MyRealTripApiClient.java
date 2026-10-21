package com.example.trippaminebe.domain.nego.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 마이리얼트립 마케팅 파트너 투어/티켓 검색 API.
 * 응답 스키마는 실계정 호출로 검증하기 전까지 JsonNode 원본 그대로 반환한다.
 * API 키는 백엔드 환경변수에서만 읽고 프론트에 노출하지 않는다.
 */
@Slf4j
@Component
public class MyRealTripApiClient {

    private static final String TNA_SEARCH_PATH = "/v1/products/tna/search";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;

    public MyRealTripApiClient(
            @Qualifier("myRealTripRestTemplate") RestTemplate restTemplate,
            @Value("${MYREALTRIP_API_BASE_URL:https://partner-ext-api.myrealtrip.com}") String baseUrl,
            @Value("${MYREALTRIP_API_KEY:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
    }

    public JsonNode searchTours(String keyword) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "마이리얼트립 API 키가 서버에 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + TNA_SEARCH_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("keyword", keyword), headers),
                    String.class
            );

            if (response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "마이리얼트립 API가 빈 응답을 반환했습니다.");
            }
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            // 파트너 API의 401을 그대로 반환하면 기존 프론트가 사용자 JWT 만료로 오판한다.
            log.warn("마이리얼트립 상품 검색 실패: upstreamStatus={}", e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "마이리얼트립 API 호출 한도에 도달했습니다.", e);
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "마이리얼트립 API 키 또는 상품 검색 권한을 확인해야 합니다.", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 상품 검색 요청이 실패했습니다.", e);
        } catch (ResourceAccessException e) {
            log.warn("마이리얼트립 상품 검색 연결 오류", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "마이리얼트립 API 연결 시간이 초과되었습니다.", e);
        } catch (JsonProcessingException e) {
            log.warn("마이리얼트립 API 응답 JSON 파싱 오류", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 API 응답 형식이 올바르지 않습니다.", e);
        } catch (RestClientException e) {
            log.warn("마이리얼트립 상품 검색 통신 오류", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 상품 검색 통신에 실패했습니다.", e);
        }
    }
}
