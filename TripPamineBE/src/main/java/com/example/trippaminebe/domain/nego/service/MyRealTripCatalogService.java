package com.example.trippaminebe.domain.nego.service;

import com.example.trippaminebe.domain.nego.client.MyRealTripApiClient;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripCatalogResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/** 기존 검색 클라이언트의 원본 JSON을 상품 카탈로그 응답으로 변환한다. */
@Service
@RequiredArgsConstructor
public class MyRealTripCatalogService {

    private static final long CACHE_TTL_NANOS = TimeUnit.MINUTES.toNanos(3);
    private static final int MAX_CACHED_SEARCHES = 128;

    private final MyRealTripApiClient myRealTripApiClient;

    // 사용자 정보와 계좌·추천 결과는 보관하지 않고, 제공사 공통 상품 목록만 단기 캐싱한다.
    // 접근 순서 기반 LRU이므로 여러 사용자가 검색하더라도 저장 크기가 제한된다.
    private final Object cacheLock = new Object();
    private final Map<String, CacheEntry> cachedSearches = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, CompletableFuture<MyRealTripCatalogResponse>> inFlightSearches = new HashMap<>();

    private record CacheEntry(MyRealTripCatalogResponse response, long savedAtNanos) {
        boolean isFresh(long nowNanos) {
            return nowNanos - savedAtNanos < CACHE_TTL_NANOS;
        }
    }

    public MyRealTripCatalogResponse searchTours(String keyword) {
        return searchTours(keyword, 1);
    }

    public MyRealTripCatalogResponse searchTours(String keyword, int page) {
        if (page < 1 || page > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지는 1~200 사이여야 합니다.");
        }
        // 공백 표기를 통일해 '  제주  '와 '제주'가 동일한 캐시 엔트리를 사용하도록 한다.
        String normalizedKeyword = keyword == null ? "" : keyword.trim().replaceAll("\\s+", " ");
        if (normalizedKeyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 검색어를 입력해주세요.");
        }

        // 캐시 키에 페이지를 포함해야 다른 페이지가 첫 페이지 결과를 덮지 않는다.
        String cacheKey = page + ":" + normalizedKeyword;
        CompletableFuture<MyRealTripCatalogResponse> waiting;
        boolean owner = false;
        synchronized (cacheLock) {
            CacheEntry cached = cachedSearches.get(cacheKey);
            if (cached != null) {
                if (cached.isFresh(System.nanoTime())) {
                    return cached.response();
                }
                cachedSearches.remove(cacheKey);
            }

            // 동시 요청이 이미 진행 중이라면 새 외부 API 호출을 시작하지 않는다.
            waiting = inFlightSearches.get(cacheKey);
            if (waiting == null) {
                waiting = new CompletableFuture<>();
                inFlightSearches.put(cacheKey, waiting);
                owner = true;
            }
        }

        if (!owner) {
            try {
                return waiting.join();
            } catch (CompletionException e) {
                // 최초 외부 요청 오류를 동일하게 전달하되 실패 응답 자체는 캐싱하지 않는다.
                if (e.getCause() instanceof RuntimeException cause) throw cause;
                throw e;
            }
        }

        try {
            MyRealTripCatalogResponse loaded = fetchAndNormalize(normalizedKeyword, page);
            synchronized (cacheLock) {
                cachedSearches.put(cacheKey, new CacheEntry(loaded, System.nanoTime()));
                // 3분 만료와 최대 128개 제한을 모두 적용한다.
                cachedSearches.entrySet().removeIf(entry -> !entry.getValue().isFresh(System.nanoTime()));
                Iterator<String> iterator = cachedSearches.keySet().iterator();
                while (cachedSearches.size() > MAX_CACHED_SEARCHES && iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
            waiting.complete(loaded);
            return loaded;
        } catch (RuntimeException | Error e) {
            waiting.completeExceptionally(e);
            throw e;
        } finally {
            synchronized (cacheLock) {
                inFlightSearches.remove(cacheKey, waiting);
            }
        }
    }

    private MyRealTripCatalogResponse fetchAndNormalize(String keyword, int requestedPage) {
        JsonNode payload = myRealTripApiClient.searchTours(keyword, requestedPage);
        JsonNode data = payload.path("data");
        JsonNode items = data.path("items");

        if (!items.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "마이리얼트립 상품 목록 응답 형식을 확인할 수 없습니다.");
        }

        List<MyRealTripCatalogResponse.Product> products = new ArrayList<>();
        for (JsonNode item : items) {
            String gid = text(item, "gid");
            String itemName = text(item, "itemName");
            JsonNode priceNode = item.path("salePrice");

            // 상품 식별자/이름/가격이 없으면 실제 판매 가능한 상품으로 오인하지 않도록 제외한다.
            if (gid == null || itemName == null || !priceNode.isNumber()) {
                continue;
            }
            BigDecimal price = priceNode.decimalValue();
            if (price.signum() < 0) {
                continue;
            }

            products.add(new MyRealTripCatalogResponse.Product(
                    gid,
                    itemName,
                    text(item, "description"),
                    price,
                    text(item, "priceDisplay"),
                    text(item, "category"),
                    number(item, "reviewScore"),
                    count(item, "reviewCount"),
                    text(item, "imageUrl"),
                    text(item, "productUrl"),
                    text(item, "deepLink"),
                    tags(item.path("tags"))
            ));
        }

        int page = positiveInt(data.path("page"), 1);
        int perPage = positiveInt(data.path("perPage"), items.size());
        int totalCount = positiveInt(data.path("totalCount"), products.size());
        if (page != requestedPage) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "요청한 투어·티켓 페이지 번호와 제공사 응답이 일치하지 않습니다.");
        }
        // hasNextPage가 응답에서 누락되어도 검증된 page/perPage/totalCount로 다음 페이지를 판별한다.
        boolean hasNextPage = data.path("hasNextPage").isBoolean()
                ? data.path("hasNextPage").booleanValue()
                : perPage > 0 && (long) page * perPage < totalCount;
        return new MyRealTripCatalogResponse(
                List.copyOf(products), totalCount, page, perPage, hasNextPage
        );
    }

    private String text(JsonNode item, String field) {
        JsonNode value = item.path(field);
        if (!value.isTextual()) {
            return null;
        }
        String trimmed = value.asText().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal number(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private Long count(JsonNode item, String field) {
        JsonNode value = item.path(field);
        return value.isIntegralNumber() && value.longValue() >= 0 ? value.longValue() : null;
    }

    private int positiveInt(JsonNode item, int fallback) {
        return item.isIntegralNumber() && item.canConvertToInt() && item.intValue() > 0
                ? item.intValue() : fallback;
    }

    private List<String> tags(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode tag : node) {
            if (tag.isTextual() && !tag.asText().isBlank()) {
                values.add(tag.asText().trim());
            }
        }
        return List.copyOf(values);
    }
}