package com.example.trippaminebe.domain.mysterytour.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Kakao Local 키워드 검색 응답 중 좌표 확정에 필요한 필드만 매핑한다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoKeywordSearchResponse(
        List<Document> documents
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y
    ) {
    }
}
