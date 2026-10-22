package com.example.trippaminebe.domain.mysterytour.client;

import com.example.trippaminebe.domain.mysterytour.dto.KakaoCategorySearchResponse;
import com.example.trippaminebe.domain.mysterytour.exception.KakaoLocalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * STEP 8.4 카테고리 검색 전용 클라이언트.
 * 기존 키워드 검색 클라이언트의 계약·기존 지도 기능은 수정하지 않는다.
 */
@Slf4j
@Component
public class KakaoCategoryLocalClient {
    // AD5는 숙박 후보의 좌표 검증에만 사용한다. 기존 관광지 검색에는 영향을 주지 않는다.
    private static final Set<String> ALLOWED_CODES = Set.of("CE7", "FD6", "CT1", "AT4", "AD5");
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String restApiKey;

    public KakaoCategoryLocalClient(
            @Qualifier("kakaoLocalRestTemplate") RestTemplate restTemplate,
            @Value("${kakao.local.base-url}") String baseUrl,
            @Value("${kakao.local.rest-api-key}") String restApiKey
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restApiKey = restApiKey;
    }

    public List<KakaoCategorySearchResponse.Document> search(
            String categoryCode, double latitude, double longitude, int radiusMeters, int page
    ) {
        // 허용 분류·좌표·반경·페이지를 반드시 검증해 불필요한 외부 호출을 막는다.
        if (!ALLOWED_CODES.contains(categoryCode) || !Double.isFinite(latitude)
                || !Double.isFinite(longitude) || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180
                || radiusMeters < 300 || radiusMeters > 3000 || page < 1 || page > 2) {
            throw new IllegalArgumentException("카테고리 검색 조건이 올바르지 않습니다.");
        }
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/v2/local/search/category.json")
                .queryParam("category_group_code", categoryCode)
                .queryParam("x", longitude)
                .queryParam("y", latitude)
                .queryParam("radius", radiusMeters)
                .queryParam("sort", "distance")
                .queryParam("size", 15)
                .queryParam("page", page)
                .build().encode().toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey);
        try {
            ResponseEntity<KakaoCategorySearchResponse> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), KakaoCategorySearchResponse.class);
            KakaoCategorySearchResponse body = response.getBody();
            return body == null || body.documents() == null ? List.of() : body.documents();
        } catch (HttpStatusCodeException exception) {
            // 사용자 위치나 API 키를 로그에 출력하지 않는다.
            log.warn("Kakao 카테고리 검색 실패. status={}", exception.getStatusCode());
            throw new KakaoLocalApiException("카테고리 검색 요청에 실패했습니다.", exception);
        } catch (KakaoLocalApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Kakao 카테고리 검색 처리 실패");
            throw new KakaoLocalApiException("카테고리 검색 중 오류가 발생했습니다.", exception);
        }
    }
}
