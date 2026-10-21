package com.example.trippaminebe.domain.nego.service;

import com.example.trippaminebe.domain.nego.dto.request.MyRealTripAccommodationSearchRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 마이리얼트립 숙소 검색 API 브리지.
 * 현재 숙소 성공 응답의 상세 필드는 실계정으로 검증되지 않았으므로 원본 JSON을 유지한다.
 * 제휴사의 데이터 스키마가 확인되기 전까지 투어용 정규화 DTO/제휴 링크 검증을 재사용하지 않는다.
 */
@Slf4j
@Service
public class MyRealTripAccommodationSearchService {

    private static final String SEARCH_PATH = "/v1/products/accommodation/search";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;

    public MyRealTripAccommodationSearchService(
            @Qualifier("myRealTripRestTemplate") RestTemplate restTemplate,
            @Value("${MYREALTRIP_API_BASE_URL:https://partner-ext-api.myrealtrip.com}") String baseUrl,
            @Value("${MYREALTRIP_API_KEY:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
    }

    public String search(MyRealTripAccommodationSearchRequest request) {
        String keyword = request.keyword().trim().replaceAll("\\s+", " ");
        if (keyword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요.");
        }

        LocalDate checkIn;
        LocalDate checkOut;
        try {
            checkIn = LocalDate.parse(request.checkIn());
            checkOut = LocalDate.parse(request.checkOut());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 숙박 날짜를 선택해주세요.", e);
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "체크아웃은 체크인보다 늦어야 합니다.");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "체크인이 지난 날짜인 숙박 상품은 검색할 수 없습니다.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "마이리얼트립 API 키가 서버에 설정되지 않았습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        int page = request.page() == null ? 0 : request.page();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyword", keyword);
        body.put("checkIn", checkIn.toString());
        body.put("checkOut", checkOut.toString());
        body.put("adultCount", request.adultCount());
        // 기존 첫 페이지 요청은 변경하지 않고, 다음 페이지에서만 page를 추가한다.
        if (page > 0) body.put("page", page);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + SEARCH_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            String result = response.getBody();
            if (result == null || result.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "마이리얼트립 숙소 검색 API가 빈 응답을 반환했습니다.");
            }
            JsonNode json = objectMapper.readTree(result);
            if (!json.isObject() || !json.path("data").path("items").isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "마이리얼트립 숙소 검색 응답의 기본 구조를 확인할 수 없습니다.");
            }
            if (page > 0 && json.path("data").path("page").asInt(-1) != page) {
                // 제공사가 요청된 페이지를 무시하면 첫 페이지를 중복 표시하지 않는다.
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "숙박 검색 페이지 요청이 반영되지 않았습니다. 제휴사 API의 페이지 파라미터를 확인해주세요.");
            }
            // Jackson 2 JsonNode가 Spring Boot 4의 Jackson 3에 잘못 직렬화되지 않도록 문자열 JSON으로 반환한다.
            return json.toString();
        } catch (HttpStatusCodeException e) {
            log.warn("마이리얼트립 숙소 검색 실패: upstreamStatus={}", e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "마이리얼트립 숙소 검색 호출 한도에 도달했습니다.", e);
            }
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                // 제휴사 401을 사용자 세션 만료 401로 오인하지 않도록 변환한다.
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "마이리얼트립 API 키 또는 숙소 검색 권한을 확인해주세요.", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 숙소 검색에 실패했습니다.", e);
        } catch (ResourceAccessException e) {
            log.warn("마이리얼트립 숙소 검색 연결 시간 초과", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "숙소 검색 서비스 응답 시간이 초과되었습니다.", e);
        } catch (JsonProcessingException e) {
            log.warn("마이리얼트립 숙소 검색 JSON 형식 오류", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "숙소 검색 응답 JSON이 올바르지 않습니다.", e);
        } catch (RestClientException e) {
            log.warn("마이리얼트립 숙소 검색 통신 오류", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "숙소 검색 서비스 통신에 실패했습니다.", e);
        }
    }
}
