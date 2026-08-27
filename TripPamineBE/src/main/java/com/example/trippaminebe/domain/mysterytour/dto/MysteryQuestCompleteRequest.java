package com.example.trippaminebe.domain.mysterytour.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

// 퀘스트 완료 요청 DTO.
// verifyType이 GPS인 퀘스트만 currentLat/currentLng를 채워서 보내면 되고,
// PHOTO/SIMPLE 타입은 지금처럼 빈 바디({})로 호출해도 그대로 동작한다(MysteryTourService에서 분기).
@Getter
@Setter
@NoArgsConstructor
public class MysteryQuestCompleteRequest {

    private BigDecimal currentLat;

    private BigDecimal currentLng;

    // GPS Horizontal Accuracy(오차 반경, m) - 선택값. 여행 퀘스트(QuestClearRequest)와 동일한 용도로,
    // 없으면 정확도 검증은 건너뛰고 거리 검증만 수행한다.
    private Double accuracyMeters;
}
