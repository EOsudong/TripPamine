package com.example.trippaminebe.domain.quest.dto.response;

import com.example.trippaminebe.domain.quest.entity.Quest;
import com.example.trippaminebe.domain.quest.entity.QuestType;
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
    private String description;
    private BigDecimal targetLat;
    private BigDecimal targetLng;
    private Long rewardPoint;
    private Long rewardExp;
    private Integer clearRadius;
    private QuestType questType;
    private Integer difficulty;
    private String imageUrl;
    private String thumbnailUrl;
    private Long chapterId;
    private String chapterName;
    private Integer requiredLevel;
    private Boolean isActive;

    // 조회하는 사용자의 현재 레벨 기준으로 계산된 잠금 여부.
    // 관리자용 조회(from(Quest))에서는 항상 false.
    private boolean locked;

    public static QuestResponse from(Quest quest) {
        return from(quest, null);
    }

    // viewerLevel이 null이면(관리자 조회 등) locked는 항상 false로 내려간다.
    public static QuestResponse from(Quest quest, Integer viewerLevel) {
        boolean locked = viewerLevel != null && viewerLevel < quest.getRequiredLevel();

        return QuestResponse.builder()
            .questId(quest.getQuestId())
            .questName(quest.getQuestName())
            .description(quest.getDescription())
            .targetLat(quest.getTargetLat())
            .targetLng(quest.getTargetLng())
            .rewardPoint(quest.getRewardPoint())
            .rewardExp(quest.getRewardExp())
            .clearRadius(quest.getClearRadius())
            .questType(quest.getQuestType())
            .difficulty(quest.getDifficulty())
            .imageUrl(quest.getImageUrl())
            .thumbnailUrl(quest.getThumbnailUrl())
            .chapterId(quest.getChapter() != null ? quest.getChapter().getChapterId() : null)
            .chapterName(quest.getChapter() != null ? quest.getChapter().getChapterName() : null)
            .requiredLevel(quest.getRequiredLevel())
            .isActive(quest.getIsActive())
            .locked(locked)
            .build();
    }
}
