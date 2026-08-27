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
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("장소 검색어가 필요합니다.");
        }

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/v2/local/search/keyword.json")
                .queryParam("query", keyword.strip())
                .queryParam("size", SEARCH_RESULT_SIZE)
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

            if (documents.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(toPlace(documents.getFirst()));
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
