package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.NearbyIndoorAlternativesResponse;
import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 실제 일정의 특정 장소 주변에서 실내 대안을 검증해 조회한다.
 * 추천 JSON/방문 시간/예약을 변경하거나 생성형 AI를 호출하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelNearbyIndoorPoiService {

    private static final int MIN_RADIUS_METERS = 300;
    private static final int MAX_RADIUS_METERS = 3000;
    private static final int MAX_RESULTS = 3;
    private static final List<String> INDOOR_TYPES = List.of("박물관", "미술관", "과학관", "아쿠아리움");

    // 시설명과 주소가 서로 다른 도시를 가리키는 오염된 검색 결과를 보수적으로 걸러낸다.
    // 행정구역 전체를 포괄하는 지오코딩 검증은 아니며, 교차 검증을 위한 1차 안전장치다.
    private static final List<String> LOCATION_NAME_PREFIXES = List.of(
            "서울", "부산", "인천", "대구", "대전", "광주", "울산", "세종",
            "수원", "성남", "용인", "고양", "부천", "경주", "전주", "강릉",
            "여수", "속초", "제주", "춘천", "청주", "천안", "포항", "창원",
            "김해", "목포", "순천", "군산", "원주", "진주", "광명"
    );

    private final TravelItineraryContextService itineraryContextService;
    private final TravelPoiResolverService poiResolverService;
    private final KakaoLocalClient kakaoLocalClient;

    public NearbyIndoorAlternativesResponse suggest(
            Long userId, Long planId, int dayNumber, int sequence, int radiusMeters
    ) {
        return suggest(userId, planId, dayNumber, sequence, radiusMeters, Set.of());
    }

    /** 동일 장소의 이전 응답을 제외할 경우에만 제외 집합을 전달한다. */
    public NearbyIndoorAlternativesResponse suggest(
            Long userId, Long planId, int dayNumber, int sequence, int radiusMeters,
            Set<String> excludedNames
    ) {
        // 요청 범위를 제한한다. 반경 3km 초과/잘못된 일차는 서버 호출 전에 거부한다.
        if (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색 반경은 300~3000m로 지정해 주세요.");
        }
        TravelItinerarySnapshot snapshot = itineraryContextService.load(userId, planId);
        TravelItinerarySnapshot.Day selectedDay = snapshot.days().stream()
                .filter(day -> day.day() == dayNumber).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 일차의 일정이 없습니다."));
        if (sequence < 1 || sequence > selectedDay.places().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "해당 순서의 방문지가 없습니다.");
        }
        TravelItinerarySnapshot.Place target = selectedDay.places().get(sequence - 1);
        String region = TravelPoiResolverService.knownRegion(snapshot.planName());

        // 한강 공원 등 지점이 여러 개인 통칭은 자동으로 특정 지점을 선택하지 않는다.
        if (isGenericPlaceName(target.name())) {
            return result(snapshot, selectedDay, target, radiusMeters,
                    "REFERENCE_AMBIGUOUS", null, List.of());
        }
        TravelPoiResolverService.Resolution reference = poiResolverService.resolve(target.name(), region);
        if (!"VERIFIED".equals(reference.status())) {
            return result(snapshot, selectedDay, target, radiusMeters,
                    "REFERENCE_" + reference.status(), null, List.of());
        }

        Map<String, NearbyIndoorAlternativesResponse.Candidate> candidates = new HashMap<>();
        int successfulSearches = 0;
        for (String indoorType : INDOOR_TYPES) {
            List<KakaoPlace> found;
            try {
                // 중심 좌표에서 반경 제한 + 거리 정렬. 검색은 유형당 한 번, 최대 네 번이다.
                found = kakaoLocalClient.searchNearbyPlaces(
                        indoorType, reference.latitude(), reference.longitude(), radiusMeters, 15);
                successfulSearches++;
            } catch (RuntimeException exception) {
                // 검색 장애는 정답 생성으로 대체하지 않고 결과 상태에 반영한다.
                log.warn("주변 실내 장소 검색 실패, type={}", indoorType);
                continue;
            }
            if (found == null) {
                continue;
            }
            for (KakaoPlace place : found) {
                if (!validCandidate(place, region, indoorType, target.name())
                        || (excludedNames != null
                        && excludedNames.contains(normalize(place.placeName())))) {
                    continue;
                }
                double lat = place.latitude().doubleValue();
                double lng = place.longitude().doubleValue();
                long straightLineMeters = Math.round(distanceMeters(
                        reference.latitude(), reference.longitude(), lat, lng));
                if (straightLineMeters > radiusMeters) {
                    continue;
                }
                // 이미 여행 일정에 포함된 장소는 대체 장소로 추천하지 않는다.
                if (alreadyScheduled(snapshot, place.placeName())) {
                    continue;
                }
                // 같은 시설의 중복 검색 결과는 명칭+주소로 합친다.
                String uniqueKey = normalize(place.placeName()) + ":" + normalize(place.addressName());
                candidates.putIfAbsent(uniqueKey, new NearbyIndoorAlternativesResponse.Candidate(
                        place.placeId(), clean(place.placeName()), clean(place.addressName()),
                        lat, lng, straightLineMeters));
            }
        }
        List<NearbyIndoorAlternativesResponse.Candidate> nearest = candidates.values().stream()
                .sorted(Comparator.comparingLong(NearbyIndoorAlternativesResponse.Candidate::straightLineMeters)
                        .thenComparing(NearbyIndoorAlternativesResponse.Candidate::name))
                .limit(MAX_RESULTS).toList();
        String status = nearest.isEmpty()
                ? (successfulSearches == 0 ? "API_UNAVAILABLE" : "NO_VERIFIED_CANDIDATES")
                : successfulSearches < INDOOR_TYPES.size() ? "PARTIAL" : "FOUND";
        return result(snapshot, selectedDay, target, radiusMeters, status, reference.address(), nearest);
    }

    private static NearbyIndoorAlternativesResponse result(
            TravelItinerarySnapshot snapshot, TravelItinerarySnapshot.Day day,
            TravelItinerarySnapshot.Place place, int radiusMeters,
            String status, String address, List<NearbyIndoorAlternativesResponse.Candidate> candidates
    ) {
        return new NearbyIndoorAlternativesResponse(
                snapshot.planId(), day.day(), place.sequence(), place.name(), place.startTime(),
                place.endTime(), status.startsWith("REFERENCE_") ? status.substring(10) : "VERIFIED",
                address, radiusMeters, status, candidates);
    }

    // 순수 필터 함수로 두어 외부 API/DB 없이 단위 테스트할 수 있게 한다.
    static boolean validCandidate(KakaoPlace place, String region, String type, String referenceName) {
        if (place == null || place.placeId() == null || place.placeId().isBlank()
                || place.placeName() == null || place.addressName() == null
                || place.latitude() == null || place.longitude() == null || region == null) {
            return false;
        }
        // 이름 끝의 시설 유형만으로 일반 관광객이 방문할 수 있다고 확정하지 않는다.
        // 박물관 내 부속시설·학교 시설·명칭의 타 지역 표시를 추가로 검증한다.
        String placeName = normalize(place.placeName());
        String address = normalize(place.addressName());
        if (!placeName.endsWith(normalize(type)) || !address.contains(normalize(region))
                || placeName.equals(normalize(referenceName))
                || containsExcludedFacilityWord(placeName)
                || hasConflictingLocationName(placeName, address)) {
            return false;
        }
        double lat = place.latitude().doubleValue();
        double lng = place.longitude().doubleValue();
        return Double.isFinite(lat) && Double.isFinite(lng)
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private static boolean containsExcludedFacilityWord(String normalizedName) {
        // 학교·연구시설에 더해 학원·교습소 등의 사설 교육시설도 기본 관광 추천에서 제외한다.
        // 이름이 "과학관"으로 끝나더라도 일반 관람 가능성을 보증하지 않기 때문이다.
        // 대학 박물관 등 공개 시설까지 제외될 수 있어 향후 운영정보로 별도 재검증한다.
        for (String word : List.of(
                "화장실", "주차장", "카페", "커피", "편의점", "매표소", "별관", "전시동",
                "학교", "대학교", "대학부설", "교내", "학생회관", "연구소", "연구원", "연구실",
                "학원", "교습소", "입시", "공부방", "독서실", "스터디카페",
                "실습실", "관리사무소", "직원전용")) {
            if (normalizedName.contains(word)) return true;
        }
        return false;
    }

    /** '울산박물관'이라는 명칭에 서울 주소가 붙는 등 명백한 도시 불일치를 차단한다. */
    private static boolean hasConflictingLocationName(String placeName, String address) {
        for (String prefix : LOCATION_NAME_PREFIXES) {
            if (placeName.startsWith(prefix) && !address.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean alreadyScheduled(TravelItinerarySnapshot snapshot, String placeName) {
        String normalizedName = normalize(placeName);
        return snapshot.days().stream().flatMap(day -> day.places().stream())
                .anyMatch(place -> normalize(place.name()).equals(normalizedName));
    }

    private static boolean isGenericPlaceName(String name) {
        String value = normalize(name);
        return value.equals("한강공원") || value.equals("한강") || value.equals("공원")
                || value.equals("홍대거리") || value.equals("이태원");
    }

    static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        // 지구 구면의 대권거리. 도보 경로/도로 거리가 아님을 UI에서 반드시 구분한다.
        double latDelta = Math.toRadians(lat2 - lat1);
        double lngDelta = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(lngDelta / 2), 2);
        return 6371000.0 * 2.0 * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHangul}A-Za-z0-9]", "");
    }

    private static String clean(String value) {
        String trimmed = value.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }
}
