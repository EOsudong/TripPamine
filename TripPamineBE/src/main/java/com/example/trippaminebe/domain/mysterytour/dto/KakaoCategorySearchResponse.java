package com.example.trippaminebe.domain.mysterytour.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Kakao Local 카테고리 검색 응답: 분류코드와 좌표를 함께 검증한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoCategorySearchResponse(List<Document> documents) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_group_code") String categoryGroupCode,
            String x,
            String y,
            @JsonProperty("place_url") String placeUrl
    ) {
        // 기존 7개 필드로 DTO를 만드는 호출부가 있다면 그대로 사용할 수 있다.
        public Document(String id, String placeName, String addressName,
                        String categoryName, String categoryGroupCode, String x, String y) {
            this(id, placeName, addressName, categoryName, categoryGroupCode, x, y, null);
        }
    }
}
