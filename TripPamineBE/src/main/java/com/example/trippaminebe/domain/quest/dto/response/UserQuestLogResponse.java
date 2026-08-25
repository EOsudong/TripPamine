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
    private QuestStatus status;
    private LocalDateTime clearDate;

    public static UserQuestLogResponse from(UserQuestLog log) {
        return UserQuestLogResponse.builder()
            .logId(log.getLogId())
            .questId(log.getQuest().getQuestId())
            .questName(log.getQuest().getQuestName())
            .rewardPoint(log.getQuest().getRewardPoint())
            .status(log.getStatus())
            .clearDate(log.getClearDate())
            .build();
    }
}
