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
    // verifyType이 GPS일 때만 값이 채워진다(그 외 타입은 null). 지도에 인증 반경 원을 그리는 용도.
    private Integer clearRadiusMeters;

    private Integer rewardPoint;
    private String status;
}
