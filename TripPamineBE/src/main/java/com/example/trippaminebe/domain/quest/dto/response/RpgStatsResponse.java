package com.example.trippaminebe.domain.quest.dto.response;

import com.example.trippaminebe.domain.quest.entity.UserRpgStats;
import com.example.trippaminebe.domain.quest.service.LevelCurve;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "사용자 RPG 스탯 응답 DTO")
public class RpgStatsResponse {

    private Integer level;
    private Integer exp;
    private Integer expToNextLevel;
    private Integer totalExp;
    private Integer totalQuests;
    private Integer completedQuests;
    private Long currentTitleId;
    private String currentTitleName;

    // 아직 USER_RPG_STATS row가 생성되지 않은 사용자(퀘스트 미시작)를 위한 초기 상태
    public static RpgStatsResponse defaultStats() {
        return RpgStatsResponse.builder()
            .level(1)
            .exp(0)
            .expToNextLevel(LevelCurve.requiredExpForLevel(1))
            .totalExp(0)
            .totalQuests(0)
            .completedQuests(0)
            .currentTitleId(null)
            .currentTitleName(null)
            .build();
    }

    public static RpgStatsResponse from(UserRpgStats stats) {
        return RpgStatsResponse.builder()
            .level(stats.getLevel())
            .exp(stats.getExp())
            .expToNextLevel(LevelCurve.requiredExpForLevel(stats.getLevel()))
            .totalExp(stats.getTotalExp())
            .totalQuests(stats.getTotalQuests())
            .completedQuests(stats.getCompletedQuests())
            .currentTitleId(stats.getCurrentTitle() != null ? stats.getCurrentTitle().getTitleId() : null)
            .currentTitleName(stats.getCurrentTitle() != null ? stats.getCurrentTitle().getTitleName() : null)
            .build();
    }
}
