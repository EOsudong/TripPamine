package com.example.trippaminebe.domain.mysterytour.dto;
import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MysteryQuestResponse {

    private Long mysteryQuestId;
    private Integer questOrder;

    private String questName;
    private String questDesc;

    private String verifyType;

    private BigDecimal targetLat;
    private BigDecimal targetLng;

    private Integer rewardPoint;
    private String status;
}