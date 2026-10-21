package com.example.trippaminebe.domain.nego.service;

import com.example.trippaminebe.domain.nego.client.MyRealTripApiClient;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripCatalogResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/** 기존 검색 클라이언트의 원본 JSON을 상품 카탈로그 응답으로 변환한다. */
@Service
@RequiredArgsConstructor
public class MyRealTripCatalogService {

    private static final int MAX_CACHE_ENTRIES = 100;
    private static final int MAX_CACHE_TTL_SECONDS = 60;

    private final MyRealTripApiClient myRealTripApiClient;

    // 상품 가격의 캐시 보관 정책이 계약상 허용된 경우에만 TTL을 설정한다(기본 0초).
    @Value("${MYREALTRIP_SEARCH_CACHE_SECONDS:0}")
    private int cacheSeconds;

    // 사용자 식별 정보는 캐시하지 않는다. 동일 검색어의 공개 상품 목록만 공유한다.
    private final ConcurrentMap<String, CachedCatalog> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<MyRealTripCatalogResponse>> inFlight =
            new ConcurrentHashMap<>();

    private record CachedCatalog(MyRealTripCatalogResponse response, long storedAtNanos) {
    }

    public MyRealTripCatalogResponse searchTours(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 검색어를 입력해주세요.");
        }
        // 동일한 검색어라도 공백과 대소문자 차이가 있으면 중복 호출되지 않도록 정규화한다.
        String normalized = keyword.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품 검색어는 80자 이내로 입력해주세요.");
        }
        String key = normalized.toLowerCase(Locale.ROOT);
        int ttl = Math.max(0, Math.min(cacheSeconds, MAX_CACHE_TTL_SECONDS));
        if (ttl > 0) {
            CachedCatalog entry = cache.get(key);
            if (entry != null) {
                if (System.nanoTime() - entry.storedAtNanos() < TimeUnit.SECONDS.toNanos(ttl)) {
                    return entry.response();
                }
                cache.remove(key, entry);
            }
        }

        // 동일 키의 동시 요청은 미래 결과를 공유하고, 실제 제휴사 호출은 한 번만 수행한다.
        CompletableFuture<MyRealTripCatalogResponse> pending = new CompletableFuture<>();
        CompletableFuture<MyRealTripCatalogResponse> existing = inFlight.putIfAbsent(key, pending);
        if (existing != null) {
            try {
                return existing.join();
            } catch (CompletionException e) {
                // 외부 API 오류 상태가 CompletionException으로 포장돼 500으로 바뀌지 않게 한다.
                if (e.getCause() instanceof RuntimeException original) {
                    throw original;
                }
                throw e;
            }
        }

        try {
            MyRealTripCatalogResponse result = fetchAndNormalize(normalized);
            if (ttl > 0) {
                putCache(key, result, ttl);
            }
            pending.complete(result);
            return result;
        } catch (RuntimeException e) {
            // 실패 응답은 캐시하지 않으므로 재시도 가능하다.
            pending.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, pending);
        }
    }

    private void putCache(String key, MyRealTripCatalogResponse response, int ttl) {
        synchronized (cache) {
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                long now = System.nanoTime();
                cache.entrySet().removeIf(entry ->
                        now - entry.getValue().storedAtNanos() >= TimeUnit.SECONDS.toNanos(ttl));
            }
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                // 새 데이터가 들어올 공간을 한 건만 확보한다. 무제한 메모리 증가 방지.
                Iterator<String> iterator = cache.keySet().iterator();
                if (iterator.hasNext()) cache.remove(iterator.next());
            }
            cache.put(key, new CachedCatalog(response, System.nanoTime()));
        }
    }

    private MyRealTripCatalogResponse fetchAndNormalize(String keyword) {
        JsonNode payload = myRealTripApiClient.searchTours(keyword);
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
        return new MyRealTripCatalogResponse(
                List.copyOf(products),
                totalCount,
                page,
                perPage,
                data.path("hasNextPage").asBoolean(false)
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
