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
            String y
    ) { }
}
