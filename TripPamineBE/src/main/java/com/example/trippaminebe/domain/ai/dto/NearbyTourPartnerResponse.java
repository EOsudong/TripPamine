package com.example.trippaminebe.domain.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * STEP 8.6-A: 관광공사 관광정보와 마이리얼트립 연관 상품을 구별한 읽기 전용 응답.
 * 관광공사 관광정보는 판매 상품이 아니며, 제휴 판매가는 결제 확정가가 아니다.
 */
public record NearbyTourPartnerResponse(
        Long planId,
        int day,
        int sequence,
        String referenceName,
        int radiusMeters,
        String referenceStatus,
        String tourApiStatus,
        List<TourPlace> tourPlaces,
        String partnerStatus,
        List<PartnerProduct> partnerProducts
) {
    public record TourPlace(
            String contentId,
            String contentTypeId,
            String title,
            String category,
            String address,
            String imageUrl,
            long straightLineMeters
    ) {
    }

    public record PartnerProduct(
            String gid,
            String itemName,
            BigDecimal salePrice,
            String productUrl,
            String relation
    ) {
    }
}
