package com.example.trippaminebe.domain.quest.dto.response;

import com.example.trippaminebe.domain.quest.entity.Quest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "퀘스트 마스터 응답 DTO")
public class QuestResponse {

    private Long questId;
    private String questName;
    private BigDecimal targetLat;
    private BigDecimal targetLng;
    private Long rewardPoint;
    private Integer clearRadius;

    public static QuestResponse from(Quest quest) {
        return QuestResponse.builder()
            .questId(quest.getQuestId())
            .questName(quest.getQuestName())
            .targetLat(quest.getTargetLat())
            .targetLng(quest.getTargetLng())
            .rewardPoint(quest.getRewardPoint())
            .clearRadius(quest.getClearRadius())
            .build();
    }
}
