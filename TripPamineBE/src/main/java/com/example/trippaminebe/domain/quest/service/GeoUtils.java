package com.example.trippaminebe.domain.quest.service;

import java.math.BigDecimal;

// Haversine 공식을 이용한 두 GPS 좌표 간 거리(m) 계산 유틸
// - 퀘스트 클리어 시 "타겟 좌표 반경 내에 실제로 있었는지" 검증하는 데 사용
public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoUtils() {
    }

    public static double distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double la1 = Math.toRadians(lat1.doubleValue());
        double la2 = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double deltaLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(la1) * Math.cos(la2)
            * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
