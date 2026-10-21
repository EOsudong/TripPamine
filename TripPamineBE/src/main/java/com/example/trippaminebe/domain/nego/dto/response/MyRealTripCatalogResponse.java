package com.example.trippaminebe.domain.nego.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** 검증된 마이리얼트립 검색 응답을 FE에서 사용하기 쉬운 형태로 정규화한 DTO. */
public record MyRealTripCatalogResponse(
        List<Product> items,
        int totalCount,
        int page,
        int perPage,
        boolean hasNextPage
) {
    public record Product(
            String gid,
            String itemName,
            String description,
            BigDecimal salePrice,
            String priceDisplay,
            String category,
            BigDecimal reviewScore,
            Long reviewCount,
            String imageUrl,
            String productUrl,
            String deepLink,
            List<String> tags
    ) {
    }
}
