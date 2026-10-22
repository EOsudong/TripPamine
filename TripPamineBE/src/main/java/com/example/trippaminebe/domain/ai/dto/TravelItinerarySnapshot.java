package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 기존 AI_TRAVEL_RECOMMENDATIONS.RECOMMEND_JSON을 읽어서 만든 읽기 전용 일정 스냅샷.
 * 카카오 장소 정보는 조회 시에만 검증하며 DB에는 기록하지 않는다.
 */
public record TravelItinerarySnapshot(
        Long planId,
        String planName,
        List<Day> days
) {
    public record Day(
            int day,
            LocalDate date,
            List<Place> places
    ) {
    }

    /**
     * scheduleKey는 조회 결과에서만 사용하는 위치 키(day/sequence)다.
     * 장소를 재정렬하면 바뀌므로 영구 식별자로 사용해서는 안 된다.
     */
    public record Place(
            String scheduleKey,
            int sequence,
            String name,
            String description,
            BigDecimal estimatedCost,
            String startTime,
            String endTime,
            String kakaoPlaceId,
            Double latitude,
            Double longitude,
            String address,
            String locationStatus
    ) {
    }
}
