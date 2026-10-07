package com.example.trippaminebe.domain.quest.dto.response;

import com.example.trippaminebe.domain.quest.entity.QuestChapter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "퀘스트 챕터 응답 DTO")
public class QuestChapterResponse {

    private Long chapterId;
    private String chapterName;
    private String description;
    private String region;
    private Integer orderNo;
    private Integer requiredLevel;
    private String imageUrl;
    private boolean locked;

    public static QuestChapterResponse from(QuestChapter chapter) {
        return from(chapter, null);
    }

    public static QuestChapterResponse from(QuestChapter chapter, Integer viewerLevel) {
        boolean locked = viewerLevel != null && viewerLevel < chapter.getRequiredLevel();

        return QuestChapterResponse.builder()
            .chapterId(chapter.getChapterId())
            .chapterName(chapter.getChapterName())
            .description(chapter.getDescription())
            .region(chapter.getRegion())
            .orderNo(chapter.getOrderNo())
            .requiredLevel(chapter.getRequiredLevel())
            .imageUrl(chapter.getImageUrl())
            .locked(locked)
            .build();
    }
}
