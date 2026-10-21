package com.example.trippaminebe.domain.nego.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** 실상품 추천 결과: 점수는 규칙 기반이며 실제 할인율이나 예약 확정 정보가 아니다. */
public record MyRealTripSmartDealResponse(
        Long planId,
        String planName,
        String keyword,
        BigDecimal tripBudget,
        BigDecimal appliedPriceCeiling,
        int totalSearchResults,
        int evaluatedCount,
        int eligibleCount,
        String priceBasis,
        String rankingType,
        List<Recommendation> items
) {
    public record Recommendation(
            MyRealTripCatalogResponse.Product product,
            int matchScore,
            List<String> reasons
    ) {
    }
}
