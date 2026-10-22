package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.NearbyCategoryAlternativesResponse;
import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import com.example.trippaminebe.domain.mysterytour.client.KakaoCategoryLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoCategorySearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * STEP 8.4: 검증된 일정 장소를 중심으로 상황별 카테고리 검색을 실행한다.
 * 일정 변경·장소 저장·LLM 호출은 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelNearbyCategoryPoiService {
    private static final int MAX_RESULTS = 6;

    private final TravelItineraryContextService itineraryContextService;
    private final TravelPoiResolverService poiResolverService;
    private final KakaoCategoryLocalClient categoryClient;
    // STEP 8.5-B: 기존 공통 POI 마스터/태그와 Kakao 분류를 일괄 검증한다.
    private final PlaceValidationPolicyService validationPolicyService;

    public enum Intent {
        RAIN(List.of("CT1", "CE7", "FD6")),
        CAFE(List.of("CE7")),
        FOOD(List.of("FD6")),
        CULTURE(List.of("CT1")),
        TOURISM(List.of("CT1", "AT4")),
        REST(List.of("CE7")),
        GENERAL(List.of("CT1", "CE7", "FD6", "AT4"));

        private final List<String> codes;
        Intent(List<String> codes) { this.codes = codes; }
    }

    /** 질문 목적을 좁은 분류부터 판정한다. 이전의 "비" 질문보다 현재 "맛집"을 우선한다. */
    public static Intent intentFor(String question) {
        String q = normalize(question);
        if (contains(q, "맛집", "음식점", "식당", "식사", "밥먹", "먹을곳")) return Intent.FOOD;
        if (contains(q, "카페", "커피", "디저트")) return Intent.CAFE;
        if (contains(q, "관광지", "명소", "볼거리", "구경")) return Intent.TOURISM;
        if (contains(q, "박물관", "미술관", "전시", "문화시설")) return Intent.CULTURE;
        if (contains(q, "잠깐쉬", "휴식", "쉴곳", "쉬어갈")) return Intent.REST;
        if (contains(q, "비", "우천", "실내", "피할곳")) return Intent.RAIN;
        return Intent.GENERAL;
    }

    public NearbyCategoryAlternativesResponse suggest(
            Long userId, Long planId, int dayNumber, int sequence,
            int radiusMeters, Intent intent, Set<String> excludedNames
    ) {
        if (radiusMeters < 300 || radiusMeters > 3000 || intent == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색 반경은 300~3000m로 지정해 주세요.");
        }
        // 기존 STEP 7과 동일한 소유권 검증을 먼저 수행한다.
        TravelItinerarySnapshot snapshot = itineraryContextService.load(userId, planId);
        TravelItinerarySnapshot.Day selectedDay = snapshot.days().stream()
                .filter(d -> d.day() == dayNumber).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 일차가 없습니다."));
        if (sequence < 1 || sequence > selectedDay.places().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 방문 순서가 없습니다.");
        }
        TravelItinerarySnapshot.Place target = selectedDay.places().get(sequence - 1);
        if (genericName(target.name())) {
            return response(snapshot, dayNumber, sequence, target.name(), radiusMeters,
                    intent, "REFERENCE_AMBIGUOUS", List.of());
        }
        String region = TravelPoiResolverService.knownRegion(snapshot.planName());
        TravelPoiResolverService.Resolution origin = poiResolverService.resolve(target.name(), region);
        if (!"VERIFIED".equals(origin.status())) {
            return response(snapshot, dayNumber, sequence, target.name(), radiusMeters,
                    intent, "REFERENCE_" + origin.status(), List.of());
        }

        Set<String> scheduled = new HashSet<>();
        for (TravelItinerarySnapshot.Day d : snapshot.days()) {
            for (TravelItinerarySnapshot.Place p : d.places()) scheduled.add(normalize(p.name()));
        }
        Set<String> excluded = excludedNames == null ? Set.of() : excludedNames;
        Map<String, List<NearbyCategoryAlternativesResponse.Candidate>> groups = new LinkedHashMap<>();
        int successfulCategories = 0;
        for (String code : intent.codes) {
            boolean categorySucceeded = false;
            // 같은 외부 장소를 중복 조회해도 정책 검증은 한 번만 수행한다.
            Map<String, KakaoCategorySearchResponse.Document> rawPlaces = new LinkedHashMap<>();
            for (int page = 1; page <= 2; page++) {
                List<KakaoCategorySearchResponse.Document> documents;
                try {
                    // 다른 후보 요청에서만 2페이지까지 읽는다. 카테고리당 최대 2회 API 호출.
                    documents = categoryClient.search(code, origin.latitude(), origin.longitude(), radiusMeters, page);
                    categorySucceeded = true;
                } catch (RuntimeException e) {
                    log.warn("주변 카테고리 조회 실패. category={}", code);
                    break;
                }
                if (documents == null || documents.isEmpty()) break;
                for (KakaoCategorySearchResponse.Document doc : documents) {
                    if (doc != null && doc.id() != null && !doc.id().isBlank()) {
                        rawPlaces.putIfAbsent(doc.id(), doc);
                    }
                }
                // 첫 응답에 이미 표시할 후보가 있어도, '다른 곳' 검색만 다음 페이지를 조회한다.
                if (excluded.isEmpty()) break;
            }
            if (categorySucceeded) successfulCategories++;
            // 외부 API 호출을 끝낸 뒤 한 번에 DB 조회: 장소별 N+1 조회를 방지한다.
            Map<String, PlaceValidationPolicyService.Verdict> verdicts =
                    validationPolicyService.evaluate(rawPlaces.values(), code, region, intent == Intent.RAIN);
            Map<String, NearbyCategoryAlternativesResponse.Candidate> unique = new HashMap<>();
            for (KakaoCategorySearchResponse.Document doc : rawPlaces.values()) {
                PlaceValidationPolicyService.Verdict verdict = verdicts.get(doc.id());
                if (verdict == null || !verdict.allowed()) continue;
                String nameKey = normalize(doc.placeName());
                if (scheduled.contains(nameKey) || excluded.contains(nameKey)) continue;
                double lat = Double.parseDouble(doc.y());
                double lng = Double.parseDouble(doc.x());
                long meters = Math.round(TravelNearbyIndoorPoiService.distanceMeters(
                        origin.latitude(), origin.longitude(), lat, lng));
                if (meters > radiusMeters) continue;
                NearbyCategoryAlternativesResponse.Candidate candidate =
                        new NearbyCategoryAlternativesResponse.Candidate(
                                doc.id(), clean(doc.placeName()), clean(doc.addressName()),
                                code, categoryLabel(code), verdict.indoorStatus(), lat, lng, meters);
                unique.putIfAbsent(nameKey + "|" + normalize(doc.addressName()), candidate);
            }
            List<NearbyCategoryAlternativesResponse.Candidate> sorted = unique.values().stream()
                    .sorted(Comparator.comparingLong(NearbyCategoryAlternativesResponse.Candidate::straightLineMeters)
                            .thenComparing(NearbyCategoryAlternativesResponse.Candidate::name))
                    .toList();
            groups.put(code, sorted);
        }

        // 한 분류가 전부 점유하지 않도록 우선 각 분류에서 최대 2곳씩 구성한다.
        List<NearbyCategoryAlternativesResponse.Candidate> selected = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (int rank = 0; rank < 2 && selected.size() < MAX_RESULTS; rank++) {
            for (String code : intent.codes) {
                List<NearbyCategoryAlternativesResponse.Candidate> group = groups.get(code);
                if (group.size() > rank && selected.size() < MAX_RESULTS) {
                    addUnique(selected, usedIds, group.get(rank));
                }
            }
        }
        // 한쪽 분류가 부족하면 나머지 분류의 검증된 후보로 빈자리를 채운다.
        if (selected.size() < MAX_RESULTS) {
            for (String code : intent.codes) {
                for (NearbyCategoryAlternativesResponse.Candidate candidate : groups.get(code)) {
                    if (selected.size() >= MAX_RESULTS) break;
                    addUnique(selected, usedIds, candidate);
                }
            }
        }
        String status = selected.isEmpty()
                ? (successfulCategories == 0 ? "API_UNAVAILABLE" : "NO_CANDIDATES")
                : successfulCategories < intent.codes.size() ? "PARTIAL" : "FOUND";
        return response(snapshot, dayNumber, sequence, target.name(), radiusMeters,
                intent, status, List.copyOf(selected));
    }

    private static void addUnique(List<NearbyCategoryAlternativesResponse.Candidate> list,
                                  Set<String> ids, NearbyCategoryAlternativesResponse.Candidate candidate) {
        if (ids.add(candidate.kakaoPlaceId())) list.add(candidate);
    }

    private static NearbyCategoryAlternativesResponse response(
            TravelItinerarySnapshot snapshot, int day, int sequence, String name,
            int radius, Intent intent, String status,
            List<NearbyCategoryAlternativesResponse.Candidate> candidates
    ) {
        return new NearbyCategoryAlternativesResponse(
                snapshot.planId(), day, sequence, name, radius, intent.name(), status, candidates);
    }

    private static String categoryLabel(String code) {
        return switch (code) {
            case "CE7" -> "카페";
            case "FD6" -> "음식점";
            case "CT1" -> "문화시설";
            case "AT4" -> "관광명소";
            default -> "기타";
        };
    }

    private static boolean genericName(String value) {
        String name = normalize(value);
        return Set.of("한강공원", "한강", "공원", "홍대거리", "이태원").contains(name);
    }

    private static boolean contains(String q, String... words) {
        for (String word : words) if (q.contains(word)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHangul}A-Za-z0-9]", "");
    }

    private static String clean(String value) {
        String safe = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return safe.length() <= 120 ? safe : safe.substring(0, 120);
    }
}
