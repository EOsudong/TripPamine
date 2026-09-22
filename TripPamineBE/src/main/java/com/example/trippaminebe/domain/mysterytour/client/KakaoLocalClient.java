package com.example.trippaminebe.domain.mysterytour.client;

import com.example.trippaminebe.domain.mysterytour.dto.KakaoKeywordSearchResponse;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
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

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/** Kakao Local 키워드 장소 검색 전담 클라이언트. */
@Slf4j
@Component
public class KakaoLocalClient {

    private static final int SEARCH_RESULT_SIZE = 5;

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String restApiKey;

    public KakaoLocalClient(
        @Qualifier("kakaoLocalRestTemplate") RestTemplate restTemplate,
        @Value("${kakao.local.base-url}") String baseUrl,
        @Value("${kakao.local.rest-api-key}") String restApiKey
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = removeTrailingSlash(baseUrl);
        this.restApiKey = restApiKey;
    }

    /** 검색 결과가 있으면 Kakao 우선순위가 가장 높은 장소 한 건을 반환한다. */
    public Optional<KakaoPlace> searchFirstPlace(String keyword) {
        List<KakaoPlace> places = searchPlaces(keyword, 1);
        return places.isEmpty() ? Optional.empty() : Optional.of(places.get(0));
    }

    /**
     *검색 결과를 최대 size건까지 그대로 반환한다.
     * 기존 searchFirstPlace()는 미스터리투어가 "장소 하나만 확정하면 되는" 용도로 쓰던 메소드라
     * 결과 1건만 돌려줬는데, AI 여행 추천 지도 편집 화면(안드로이드 네이티브)은 두 가지 용도로
     * 여러 건이 필요하다:
     * 1) AI가 추천한 장소 이름 하나하나를 지도 좌표로 바꿀 때 - 가장 가까운(관련도 높은) 결과를 고른다.
     * 2) 사용자가 직접 "장소 추가" 검색창에 검색어를 입력했을 때 - 여러 후보 중 하나를 고르게 한다.
     * searchFirstPlace()는 이제 이 메소드를 size=1로 호출하는 것으로 재구현했다
     */
    public List<KakaoPlace> searchPlaces(String keyword, int size) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("장소 검색어가 필요합니다.");
        }

        int boundedSize = Math.max(1, Math.min(size, 15));

        URI uri = UriComponentsBuilder
            .fromUriString(baseUrl + "/v2/local/search/keyword.json")
            .queryParam("query", keyword.strip())
            .queryParam("size", boundedSize)
            .build()
            .encode()
            .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey);

        try {
            ResponseEntity<KakaoKeywordSearchResponse> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                KakaoKeywordSearchResponse.class
            );

            KakaoKeywordSearchResponse body = response.getBody();
            List<KakaoKeywordSearchResponse.Document> documents =
                body == null || body.documents() == null ? List.of() : body.documents();

            return documents.stream().map(this::toPlace).toList();
        } catch (HttpStatusCodeException e) {
            log.error("Kakao Local API 호출 실패 (status={}, keyword={})", e.getStatusCode(), keyword);
            throw new KakaoLocalApiException(
                "Kakao 장소 검색에 실패했습니다. HTTP " + e.getStatusCode().value(),
                e
            );
        } catch (KakaoLocalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kakao Local API 호출 중 오류 발생 (keyword={})", keyword, e);
            throw new KakaoLocalApiException("Kakao 장소 검색 중 오류가 발생했습니다.", e);
        }
    }

    private KakaoPlace toPlace(KakaoKeywordSearchResponse.Document document) {
        try {
            // Kakao Local 응답은 x=경도, y=위도다.
            return new KakaoPlace(
                document.id(),
                document.placeName(),
                document.addressName(),
                document.roadAddressName(),
                new BigDecimal(document.y()),
                new BigDecimal(document.x())
            );
        } catch (RuntimeException e) {
            throw new KakaoLocalApiException("Kakao 장소 검색 응답의 좌표 형식이 올바르지 않습니다.", e);
        }
    }

    private static String removeTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
