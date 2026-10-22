package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.mysterytour.dto.KakaoCategorySearchResponse;
import com.example.trippaminebe.domain.poi.entity.PlaceMaster;
import com.example.trippaminebe.domain.poi.entity.PlaceTagEntity;
import com.example.trippaminebe.domain.poi.repository.PlaceMasterRepository;
import com.example.trippaminebe.domain.poi.repository.PlaceTagRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * STEP 8.5-B: Kakao 카테고리 + 이미 등록된 PLACES/PLACE_TAGS를 교차 확인한다.
 * 명칭 제외 규칙은 Java 코드가 아닌 poi-validation.properties에서 관리한다.
 * 실제 운영/입장 여부는 이 검증만으로 확인할 수 없으며 장소를 자동 저장하지 않는다.
 */
@Service
@PropertySource(value = "classpath:poi-validation.properties", encoding = "UTF-8")
public class PlaceValidationPolicyService {

    private final PlaceMasterRepository placeRepository;
    private final PlaceTagRepository tagRepository;
    private final List<String> excludedTokens;
    private final List<String> regionPrefixes;

    public PlaceValidationPolicyService(
            PlaceMasterRepository placeRepository,
            PlaceTagRepository tagRepository,
            @Value("${trippamine.poi.excluded-tokens}") String excludedTokens,
            @Value("${trippamine.poi.region-prefixes}") String regionPrefixes
    ) {
        this.placeRepository = placeRepository;
        this.tagRepository = tagRepository;
        this.excludedTokens = csvTokens(excludedTokens);
        this.regionPrefixes = csvTokens(regionPrefixes);
    }

    /**
     * Kakao의 현재 검색 결과를 한 번에 평가한다.
     * 내부 마스터에 없는 장소는 무조건 배제하지 않고 외부 기본 검증 결과를 유지한다.
     */
    @Transactional(readOnly = true)
    public Map<String, Verdict> evaluate(
            Collection<KakaoCategorySearchResponse.Document> docs,
            String expectedCategory, String region, boolean rain
    ) {
        Map<String, Verdict> result = new LinkedHashMap<>();
        if (docs == null || docs.isEmpty()) return result;

        // 검증할 ID만 모아서 카테고리당 최대 1번의 장소 조회를 수행한다.
        List<KakaoCategorySearchResponse.Document> valid = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (KakaoCategorySearchResponse.Document doc : docs) {
            if (doc == null || doc.id() == null || doc.id().isBlank()) continue;
            if (!basicValid(doc, expectedCategory, region)) {
                result.put(doc.id(), Verdict.rejected());
                continue;
            }
            valid.add(doc);
            ids.add(doc.id());
        }
        if (ids.isEmpty()) return result;

        Map<String, PlaceMaster> masterByExternalId = new HashMap<>();
        for (PlaceMaster place : placeRepository.findBySourceTypeAndSourcePlaceIdIn("KAKAO", ids)) {
            if (place.getSourcePlaceId() != null) {
                masterByExternalId.put(place.getSourcePlaceId(), place);
            }
        }
        Set<Long> placeIds = new HashSet<>();
        for (PlaceMaster place : masterByExternalId.values()) {
            if (place.getPlaceId() != null) placeIds.add(place.getPlaceId());
        }
        Map<Long, Set<String>> environmentTags = new HashMap<>();
        if (!placeIds.isEmpty()) {
            for (PlaceTagEntity tag : tagRepository.findByPlaceIdIn(placeIds)) {
                if ("ENVIRONMENT".equalsIgnoreCase(tag.getTagType()) && tag.getTagValue() != null) {
                    environmentTags.computeIfAbsent(tag.getPlaceId(), unused -> new HashSet<>())
                            .add(tag.getTagValue().trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        for (KakaoCategorySearchResponse.Document doc : valid) {
            PlaceMaster place = masterByExternalId.get(doc.id());
            if (place != null && !"Y".equalsIgnoreCase(place.getActiveYn())) {
                result.put(doc.id(), Verdict.rejected());
                continue;
            }
            Set<String> tags = place == null ? Set.of()
                    : environmentTags.getOrDefault(place.getPlaceId(), Set.of());
            // 비 피하기 요청에서 OUTDOOR 기록이 있는 장소는 실내 대안으로 내보내지 않는다.
            if (rain && tags.contains("OUTDOOR")) {
                result.put(doc.id(), Verdict.rejected());
                continue;
            }
            // 'INDOOR'는 내부 태그의 주장이다. 실제 운영/실내 접근 가능성의 실시간 검증이 아니다.
            result.put(doc.id(), new Verdict(true,
                    tags.contains("INDOOR") && !tags.contains("OUTDOOR")
                            ? "TAGGED_INDOOR" : "UNVERIFIED"));
        }
        return result;
    }

    /** 좌표·지역·카테고리·명백한 비관광시설을 Kakao 제공값과 설정 정책으로 검사한다. */
    boolean basicValid(KakaoCategorySearchResponse.Document doc, String code, String region) {
        if (doc == null || doc.id() == null || doc.id().isBlank()
                || doc.placeName() == null || doc.placeName().isBlank()
                || doc.addressName() == null || region == null
                || !code.equals(doc.categoryGroupCode())
                || !normalize(doc.addressName()).contains(normalize(region))) return false;
        String name = normalize(doc.placeName());
        String detail = normalize(doc.categoryName());
        if ("CT1".equals(code) || "AT4".equals(code)) {
            for (String term : excludedTokens) {
                if (name.contains(term) || detail.contains(term)) return false;
            }
        }
        for (String prefix : regionPrefixes) {
            if (name.startsWith(prefix) && !normalize(doc.addressName()).contains(prefix)) return false;
        }
        try {
            double lat = Double.parseDouble(doc.y());
            double lng = Double.parseDouble(doc.x());
            return Double.isFinite(lat) && lat >= -90 && lat <= 90
                    && Double.isFinite(lng) && lng >= -180 && lng <= 180;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<String> csvTokens(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(PlaceValidationPolicyService::normalize)
                .filter(v -> !v.isBlank()).distinct().toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHangul}A-Za-z0-9]", "");
    }

    public record Verdict(boolean allowed, String indoorStatus) {
        static Verdict rejected() { return new Verdict(false, "UNVERIFIED"); }
    }
}
