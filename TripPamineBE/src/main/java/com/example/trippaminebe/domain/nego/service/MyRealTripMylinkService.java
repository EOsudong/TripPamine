package com.example.trippaminebe.domain.nego.service;

import com.example.trippaminebe.domain.nego.dto.request.MyRealTripMylinkRequest;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripMylinkResponse;
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

import java.net.URI;
import java.util.Map;

/** 서버에서만 파트너 키를 사용해 MyLink 링크를 발급받는다. */
@Slf4j
@Service
public class MyRealTripMylinkService {

    private static final String MYLINK_PATH = "/v1/mylink";
    private static final String PRODUCT_HOST = "experiences.myrealtrip.com";
    private static final String MYLINK_HOST = "myrealt.rip";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final String apiKey;

    public MyRealTripMylinkService(
            @Qualifier("myRealTripRestTemplate") RestTemplate restTemplate,
            @Value("${MYREALTRIP_API_BASE_URL:https://partner-ext-api.myrealtrip.com}") String baseUrl,
            @Value("${MYREALTRIP_API_KEY:}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
    }

    public MyRealTripMylinkResponse create(MyRealTripMylinkRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "마이리얼트립 API 키가 서버에 설정되지 않았습니다.");
        }

        // 서버가 임의의 외부 URL로 MyLink를 생성하지 않도록 상품 도메인/경로와 ID를 확인한다.
        String productUrl = validateProductUrl(request.gid(), request.productUrl());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey.trim());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + MYLINK_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("targetUrl", productUrl), headers),
                    String.class
            );
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "마이리얼트립 MyLink API가 빈 응답을 반환했습니다.");
            }

            JsonNode data = objectMapper.readTree(responseBody).path("data");
            // 실제 응답이 아직 수집되지 않았으므로 공개 사례의 두 가지 data 표현만 허용한다.
            String link = data.isTextual() ? data.asText() : text(data.path("mylink"));
            if (link == null || !isOfficialMylink(link)) {
                log.warn("MyLink 응답 검증 실패: dataNodeType={}", data.getNodeType());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "마이리얼트립 MyLink 응답 형식이 예상과 다릅니다. 응답 스키마를 확인해야 합니다.");
            }
            return new MyRealTripMylinkResponse(link);
        } catch (HttpStatusCodeException e) {
            // 외부 API 인증 실패를 401로 프론트에 전달하면 사용자 세션이 잘못 로그아웃된다.
            log.warn("마이리얼트립 MyLink 생성 실패: upstreamStatus={}", e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "마이리얼트립 MyLink 호출 한도에 도달했습니다.", e);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 MyLink 생성 요청이 거부되거나 실패했습니다.", e);
        } catch (ResourceAccessException e) {
            log.warn("마이리얼트립 MyLink 연결 오류", e);
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "마이리얼트립 MyLink API 연결 시간이 초과되었습니다.", e);
        } catch (JsonProcessingException e) {
            log.warn("마이리얼트립 MyLink JSON 파싱 오류", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 MyLink 응답 JSON이 올바르지 않습니다.", e);
        } catch (RestClientException e) {
            log.warn("마이리얼트립 MyLink 통신 오류", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 MyLink 통신에 실패했습니다.", e);
        }
    }

    private String validateProductUrl(String gid, String value) {
        if (gid == null || !gid.matches("[0-9]{1,20}") || value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 상품 정보입니다.");
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getPort() != -1
                    || uri.getUserInfo() != null
                    || uri.getRawFragment() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "마이리얼트립 공식 HTTPS 상품 주소만 사용할 수 있습니다.");
            }

            String host = uri.getHost();
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            // 기존 투어 URL 정책은 변경하지 않는다.
            boolean isTour = PRODUCT_HOST.equalsIgnoreCase(host)
                    && ("/products/" + gid).equals(path)
                    && query == null;

            // 숙박 검색 실제 응답에서 검증된 URL 형식만 추가 허용한다.
            // 일반 호스트/임의 쿼리를 허용하면 추적·보안상 문제가 생길 수 있다.
            boolean isAccommodation = "accommodation.myrealtrip.com".equalsIgnoreCase(host)
                    && ("/union/products/" + gid).equals(path)
                    && query != null
                    && query.matches("checkIn=\\d{4}-\\d{2}-\\d{2}&checkOut=\\d{4}-\\d{2}-\\d{2}&adultCount=(?:[1-9]|1\\d|20)&childCount=0&childAges=");

            if (!isTour && !isAccommodation) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "상품 ID와 공식 상품 URL 형식이 일치하지 않습니다.");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "올바른 마이리얼트립 상품 주소가 아닙니다.", e);
        }
    }

    private String text(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText().trim() : null;
    }

    private boolean isOfficialMylink(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && MYLINK_HOST.equalsIgnoreCase(uri.getHost())
                    && uri.getPort() == -1
                    && uri.getUserInfo() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}