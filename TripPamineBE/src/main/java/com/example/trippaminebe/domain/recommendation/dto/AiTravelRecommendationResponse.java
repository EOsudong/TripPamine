package com.example.trippaminebe.domain.recommendation.dto;

import com.example.trippaminebe.domain.recommendation.entity.AiTravelRecommendation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class AiTravelRecommendationResponse {

    private Long recommendId;
    private Long planId;
    private String recommendJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AiTravelRecommendationResponse from(
            AiTravelRecommendation recommendation
    ) {
        return AiTravelRecommendationResponse.builder()
                .recommendId(recommendation.getRecommendId())
                .planId(recommendation.getTravelPlan().getPlanId())
                .recommendJson(recommendation.getRecommendJson())
                .createdAt(recommendation.getCreatedAt())
                .updatedAt(recommendation.getUpdatedAt())
                .build();
    }
}