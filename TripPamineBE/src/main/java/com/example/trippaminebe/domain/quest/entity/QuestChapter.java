package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// QUEST_CHAPTERS: 지역별 퀘스트 묶음(챕터).
@Entity
@Table(name = "QUEST_CHAPTERS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestChapter {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_QUEST_CHAPTERS_GEN")
    @SequenceGenerator(name = "SEQ_QUEST_CHAPTERS_GEN", sequenceName = "SEQ_QUEST_CHAPTERS", allocationSize = 1)
    @Column(name = "CHAPTER_ID")
    private Long chapterId;

    @Column(name = "CHAPTER_NAME", nullable = false, length = 100)
    private String chapterName;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "REGION", length = 50)
    private String region;

    @Column(name = "ORDER_NO", nullable = false)
    @Builder.Default
    private Integer orderNo = 0;

    @Column(name = "REQUIRED_LEVEL", nullable = false)
    @Builder.Default
    private Integer requiredLevel = 1;

    @Column(name = "IMAGE_URL", length = 500)
    private String imageUrl;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // 챕터 정보 수정(관리자용) 도메인 메서드
    public void update(String chapterName, String description, String region,
                        Integer orderNo, Integer requiredLevel, String imageUrl) {
        this.chapterName = chapterName;
        this.description = description;
        this.region = region;
        this.orderNo = orderNo != null ? orderNo : this.orderNo;
        this.requiredLevel = requiredLevel != null ? requiredLevel : this.requiredLevel;
        this.imageUrl = imageUrl;
        this.updatedAt = LocalDateTime.now();
    }
}
