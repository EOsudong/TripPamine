package com.example.trippaminebe.domain.quest.dto.response;

import com.example.trippaminebe.domain.quest.entity.QuestStep;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "연쇄 퀘스트 단계 응답 DTO")
public class QuestStepResponse {

    private Long stepId;
    private Long questId;
    private Integer stepOrder;
    private String stepName;
    private String description;
    private BigDecimal targetLat;
    private BigDecimal targetLng;
    private Integer clearRadius;

    // 조회하는 사용자가 이미 이 단계를 클리어했는지. 관리자 조회(from(step))에서는 항상 false.
    private boolean cleared;

    public static QuestStepResponse from(QuestStep step) {
        return from(step, Set.of());
    }

    public static QuestStepResponse from(QuestStep step, Set<Long> clearedStepIds) {
        return QuestStepResponse.builder()
            .stepId(step.getStepId())
            .questId(step.getQuest().getQuestId())
            .stepOrder(step.getStepOrder())
            .stepName(step.getStepName())
            .description(step.getDescription())
            .targetLat(step.getTargetLat())
            .targetLng(step.getTargetLng())
            .clearRadius(step.getClearRadius())
            .cleared(clearedStepIds.contains(step.getStepId()))
            .build();
    }
}
