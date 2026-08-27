package com.example.trippaminebe.domain.mysterytour.dto;

import java.math.BigDecimal;

/** Kakao 검색 결과를 TripPamine 내부 좌표 규칙(lat/lng)으로 정규화한 값. */
public record KakaoPlace(
        String placeId,
        String placeName,
        String addressName,
        String roadAddressName,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
