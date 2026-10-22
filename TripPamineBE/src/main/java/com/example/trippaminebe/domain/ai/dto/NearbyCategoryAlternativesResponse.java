package com.example.trippaminebe.domain.ai.dto;

import java.util.List;

/**
 * STEP 8.4: 현재 여행 방문지를 중심으로 한 분류별 장소 후보.
 * 직선거리만 제공하며 실내 여부·운영시간·입장 가능성은 확정하지 않는다.
 */
public record NearbyCategoryAlternativesResponse(
        Long planId,
        int day,
        int sequence,
        String referenceName,
        int radiusMeters,
        String intent,
        String status,
        List<Candidate> candidates
) {
    public record Candidate(
            String kakaoPlaceId,
            String name,
            String address,
            String categoryCode,
            String categoryLabel,
            String indoorStatus,
            double latitude,
            double longitude,
            long straightLineMeters
    ) { }
}
