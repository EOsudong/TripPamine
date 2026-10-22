package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.mysterytour.client.KakaoLocalClient;
import com.example.trippaminebe.domain.mysterytour.dto.KakaoPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 장소 이름을 GPS 좌표로 바꾸는 읽기 전용 검증기.
 * 동명이인 장소나 지역 불일치 후보를 첫 결과라는 이유로 선택하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelPoiResolverService {

    private final KakaoLocalClient kakaoLocalClient;

    public record Resolution(
            String status,
            String placeId,
            Double latitude,
            Double longitude,
            String address
    ) {
        public static Resolution unresolved(String status) {
            return new Resolution(status, null, null, null, null);
        }
    }

    /**
     * 여행 이름에 도시명이 명확한 경우에만 지역을 제한한다.
     * LOCATION_CD는 운영 DB의 코드 정의를 확인하기 전까지 임의 해석하지 않는다.
     */
    public static String knownRegion(String planName) {
        if (planName == null) {
            return null;
        }
        for (String region : List.of(
                "서울", "부산", "인천", "대구", "대전", "광주", "울산",
                "제주", "세종", "수원", "경주", "전주", "강릉", "여수", "속초"
        )) {
            if (planName.contains(region)) {
                return region;
            }
        }
        return null;
    }

    public Resolution resolve(String placeName, String region) {
        // 권역을 알 수 없는 경우 다른 도시의 동명 장소를 잘못 확정하지 않는다.
        if (placeName == null || placeName.isBlank() || region == null || region.isBlank()) {
            return Resolution.unresolved("REGION_UNKNOWN");
        }
        if (placeName.length() > 100) {
            return Resolution.unresolved("NOT_FOUND");
        }

        final List<KakaoPlace> candidates;
        try {
            candidates = kakaoLocalClient.searchPlaces(region + " " + placeName, 10);
        } catch (RuntimeException exception) {
            // 외부 서비스 실패는 위치 검증 실패로 구별한다. 좌표나 검색어는 로그에 넣지 않는다.
            log.warn("여행 POI 좌표 검색 실패. 외부 장소 API 상태를 확인하세요.");
            return Resolution.unresolved("API_ERROR");
        }

        if (candidates == null || candidates.isEmpty()) {
            return Resolution.unresolved("NOT_FOUND");
        }

        String expectedName = normalize(placeName);
        List<KakaoPlace> verified = new ArrayList<>();
        for (KakaoPlace candidate : candidates) {
            if (candidate == null || candidate.placeId() == null
                    || candidate.placeName() == null || candidate.addressName() == null
                    || candidate.latitude() == null || candidate.longitude() == null) {
                continue;
            }
            // 명칭 및 주소가 모두 맞을 때만 좌표를 확정한다.
            // '이태원'과 '이태원역'처럼 부분 일치하는 결과는 자동 확정하지 않는다.
            if (!normalize(candidate.placeName()).equals(expectedName)
                    || !candidate.addressName().contains(region)) {
                continue;
            }
            double lat = candidate.latitude().doubleValue();
            double lng = candidate.longitude().doubleValue();
            if (!Double.isFinite(lat) || lat < -90 || lat > 90
                    || !Double.isFinite(lng) || lng < -180 || lng > 180) {
                continue;
            }
            if (verified.stream().noneMatch(p -> p.placeId().equals(candidate.placeId()))) {
                verified.add(candidate);
            }
        }

        if (verified.isEmpty()) {
            return Resolution.unresolved("NOT_FOUND");
        }
        if (verified.size() > 1) {
            return Resolution.unresolved("AMBIGUOUS");
        }
        KakaoPlace place = verified.get(0);
        return new Resolution(
                "VERIFIED", place.placeId(), place.latitude().doubleValue(),
                place.longitude().doubleValue(), sanitize(place.addressName())
        );
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHangul}A-Za-z0-9]", "");
    }

    private static String sanitize(String value) {
        String clean = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120);
    }
}
