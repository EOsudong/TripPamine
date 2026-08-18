package com.example.trippaminebe.domain.travel.dto;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class TravelPlanResponse {

    private Long planId;
    private String planName;
    private BigDecimal totalBudget;
    private String companionType;
    private String locationCd;
    private String blindYn;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    public static TravelPlanResponse from(TravelPlan plan) {
        return TravelPlanResponse.builder()
            .planId(plan.getPlanId())
            .planName(plan.getPlanName())
            .totalBudget(plan.getTotalBudget())
            .companionType(plan.getCompanionType())
            .locationCd(plan.getLocationCd())
            .blindYn(plan.getBlindYn())
            .startDate(plan.getStartDate())
            .endDate(plan.getEndDate())
            .build();
    }
}