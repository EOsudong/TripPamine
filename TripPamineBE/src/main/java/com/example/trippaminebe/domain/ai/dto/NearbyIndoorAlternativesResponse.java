package com.example.trippaminebe.domain.ai.dto;

import java.util.List;

/**
 * STEP 8: 인증된 여행 일정의 한 방문지를 중심으로 검색한 읽기 전용 실내 후보.
 * 거리(m)는 직선거리이며 도보·차량 이동 거리 또는 소요 시간이 아니다.
 */
public record NearbyIndoorAlternativesResponse(
        Long planId,
        int day,
        int sequence,
        String referenceName,
        String referenceStartTime,
        String referenceEndTime,
        String referenceLocationStatus,
        String referenceAddress,
        int radiusMeters,
        String status,
        List<Candidate> candidates
) {
    public record Candidate(
            String kakaoPlaceId,
            String name,
            String address,
            double latitude,
            double longitude,
            long straightLineMeters
    ) {
    }
}
