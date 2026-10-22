package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.NearbyTourPartnerResponse;
import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripCatalogResponse;
import com.example.trippaminebe.domain.nego.service.MyRealTripCatalogService;
import com.example.trippaminebe.domain.tour.dto.TourItemResponse;
import com.example.trippaminebe.domain.tour.service.TourCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * STEP 8.6-A: 저장된 방문지 주변 관광공사 콘텐츠 + 마이리얼트립 연관 상품 결합.
 * - 사용자 요청 중 TourAPI 외부 호출 없음: 기존 TOUR_CACHE_ENTRY만 조회.
 * - 상품 검색은 includePartner=true일 때만 실행하여 제휴 API 사용량을 제한.
 * - 상품명에 장소명이 포함되어도 같은 시설/입장권으로 단정하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TourPartnerDiscoveryService {

    private static final int MAX_TOUR_PLACES = 4;
    private static final int MAX_PARTNER_PRODUCTS = 3;
    private static final List<String> TOUR_CACHE_KEYS = List.of(
            "destinations:전체", "destinations:체험 관광", "destinations:문화 관광",
            "industry:레저 스포츠");

    private final TravelItineraryContextService itineraryContextService;
    private final TravelPoiResolverService poiResolverService;
    private final TourCacheService tourCacheService;
    private final MyRealTripCatalogService catalogService;

    public NearbyTourPartnerResponse suggest(Long userId, Long planId, int day, int sequence,
                                              int radiusMeters, boolean includePartner,
                                              Set<String> excludedNames) {
        if (radiusMeters < 300 || radiusMeters > 3000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색 반경은 300~3000m입니다.");
        }
        // 소유권 검증 후 저장된 일정에서 기준 장소를 확정한다. 일정은 변경하지 않는다.
        TravelItinerarySnapshot snapshot = itineraryContextService.load(userId, planId);
        TravelItinerarySnapshot.Day travelDay = snapshot.days().stream()
                .filter(d -> d.day() == day).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "일차가 없습니다."));
        if (sequence < 1 || sequence > travelDay.places().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "방문 순서가 없습니다.");
        }
        String referenceName = travelDay.places().get(sequence - 1).name();
        String normalizedReference = normalize(referenceName);
        // 기존 경복궁 등 단일 장소 검색과 달리 한강공원처럼 일반명칭은 좌표 자동 확정 금지.
        if (TravelNearbyCategoryPoiService.genericName(referenceName)) {
            return empty(planId, day, sequence, referenceName, radiusMeters, "AMBIGUOUS");
        }
        String region = TravelPoiResolverService.knownRegion(snapshot.planName());
        TravelPoiResolverService.Resolution origin = poiResolverService.resolve(referenceName, region);
        if (!"VERIFIED".equals(origin.status()) || origin.latitude() == null || origin.longitude() == null) {
            return empty(planId, day, sequence, referenceName, radiusMeters, origin.status());
        }

        Set<String> excluded = new HashSet<>();
        if (excludedNames != null) {
            for (String name : excludedNames) excluded.add(normalize(name));
        }
        for (TravelItinerarySnapshot.Day d : snapshot.days()) {
            for (TravelItinerarySnapshot.Place p : d.places()) excluded.add(normalize(p.name()));
        }

        String tourStatus;
        List<NearbyTourPartnerResponse.TourPlace> tourPlaces;
        try {
            List<TourItemResponse> cached = tourCacheService.getCachedMany(TOUR_CACHE_KEYS);
            Map<String, NearbyTourPartnerResponse.TourPlace> unique = new LinkedHashMap<>();
            for (TourItemResponse place : cached) {
                if (place == null || place.getContentId() == null || place.getContentId().isBlank()
                        || place.getTitle() == null || place.getTitle().isBlank()
                        || place.getMapX() == null || place.getMapY() == null
                        || !Double.isFinite(place.getMapX()) || !Double.isFinite(place.getMapY())
                        || !validCoordinates(place.getMapY(), place.getMapX())
                        || excluded.contains(normalize(place.getTitle()))) continue;
                long distance = Math.round(TravelNearbyIndoorPoiService.distanceMeters(
                        origin.latitude(), origin.longitude(), place.getMapY(), place.getMapX()));
                if (distance > radiusMeters) continue;
                // 동일 contentId는 관광공사 내부에서 같은 관광정보다.
                unique.putIfAbsent(place.getContentId(), new NearbyTourPartnerResponse.TourPlace(
                        place.getContentId(), safe(place.getContentTypeId()), safe(place.getTitle()),
                        safe(place.getCategory()), safe(place.getAddress()), safe(place.getImageUrl()), distance));
            }
            tourPlaces = unique.values().stream()
                    .sorted(Comparator.comparingLong(NearbyTourPartnerResponse.TourPlace::straightLineMeters)
                            .thenComparing(NearbyTourPartnerResponse.TourPlace::title))
                    .limit(MAX_TOUR_PLACES).toList();
            tourStatus = cached.isEmpty() ? "CACHE_EMPTY" : tourPlaces.isEmpty() ? "NO_NEARBY_IN_CACHE" : "FOUND";
        } catch (RuntimeException exception) {
            log.warn("TourAPI 주변 관광정보 캐시 조회 실패");
            tourPlaces = List.of();
            tourStatus = "CACHE_UNAVAILABLE";
        }

        String partnerStatus = "NOT_REQUESTED";
        List<NearbyTourPartnerResponse.PartnerProduct> partnerProducts = List.of();
        if (includePartner) {
            try {
                // 조회된 '판매가'를 제공하되 시설 자체의 입장권과 혼동하지 않는다.
                MyRealTripCatalogResponse catalog = catalogService.searchTours(referenceName);
                partnerProducts = catalog.items().stream()
                        .filter(p -> p.gid() != null && p.gid().matches("[0-9]{1,20}")
                                && p.itemName() != null && p.salePrice() != null
                                && p.salePrice().compareTo(BigDecimal.ZERO) >= 0
                                && normalize(p.itemName()).contains(normalizedReference)
                                && officialTourUrl(p.gid(), p.productUrl()))
                        .limit(MAX_PARTNER_PRODUCTS)
                        .map(p -> new NearbyTourPartnerResponse.PartnerProduct(
                                p.gid(), safe(p.itemName()), p.salePrice(), p.productUrl(),
                                "RELATED_PRODUCT_NOT_VERIFIED_AS_ENTRY_TICKET"))
                        .toList();
                partnerStatus = partnerProducts.isEmpty() ? "NO_RELATED_PRODUCTS" : "FOUND";
            } catch (RuntimeException exception) {
                // 제휴 API 장애가 관광공사/기존 POI 추천까지 중단시키면 안 된다.
                log.warn("마이리얼트립 연관 상품 검색 실패");
                partnerStatus = "API_UNAVAILABLE";
            }
        }
        return new NearbyTourPartnerResponse(planId, day, sequence, referenceName,
                radiusMeters, "VERIFIED", tourStatus, tourPlaces, partnerStatus, partnerProducts);
    }

    private static NearbyTourPartnerResponse empty(Long planId, int day, int sequence,
                                                    String reference, int radius, String status) {
        return new NearbyTourPartnerResponse(planId, day, sequence, reference, radius,
                status, "NOT_REQUESTED", List.of(), "NOT_REQUESTED", List.of());
    }

    private static boolean validCoordinates(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /** 상품 URL은 기존 MyLinkService와 같은 공식 투어 상품 URL만 허용한다. */
    private static boolean officialTourUrl(String gid, String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "experiences.myrealtrip.com".equalsIgnoreCase(uri.getHost())
                    && ("/products/" + gid).equals(uri.getRawPath())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getUserInfo() == null && uri.getPort() == -1;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHangul}A-Za-z0-9]", "");
    }

    private static String safe(String text) {
        if (text == null) return null;
        String value = text.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return value.length() > 160 ? value.substring(0, 160) : value;
    }
}
