package com.example.trippaminebe.domain.quest.dto.response;

import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.entity.UserQuestLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "유저 퀘스트 수행 로그 응답 DTO")
public class UserQuestLogResponse {

    private Long logId;
    private Long questId;
    private String questName;
    private Long rewardPoint;
    private Long rewardExp;
    private QuestStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime clearDate;

    // 클리어 시점 보상 스냅샷(아직 미클리어면 null)
    private Long earnedPoint;
    private Long earnedExp;

    // 연쇄(CHAIN) 퀘스트 진행률(N/M). 일반 퀘스트거나 아직 계산하지 않은 조회에서는 둘 다 null.
    private Integer clearedStepCount;
    private Integer totalStepCount;

    public static UserQuestLogResponse from(UserQuestLog log) {
        return from(log, null, null);
    }

    public static UserQuestLogResponse from(UserQuestLog log, Integer clearedStepCount, Integer totalStepCount) {
        return UserQuestLogResponse.builder()
            .logId(log.getLogId())
            .questId(log.getQuest().getQuestId())
            .questName(log.getQuest().getQuestName())
            .rewardPoint(log.getQuest().getRewardPoint())
            .rewardExp(log.getQuest().getRewardExp())
            .status(log.getStatus())
            .startedAt(log.getStartedAt())
            .clearDate(log.getClearDate())
            .earnedPoint(log.getEarnedPoint())
            .earnedExp(log.getEarnedExp())
            .clearedStepCount(clearedStepCount)
            .totalStepCount(totalStepCount)
            .build();
    }
}
