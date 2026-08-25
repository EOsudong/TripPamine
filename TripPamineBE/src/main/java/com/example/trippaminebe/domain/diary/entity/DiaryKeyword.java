package com.example.trippaminebe.domain.diary.entity;

import jakarta.persistence.*;
import lombok.*;

// DIARY_KEYWORDS: AI 다이어리에서 추출된 키워드를 유형별(감정/소비/장소)로 분리 저장
@Entity
@Table(name = "DIARY_KEYWORDS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaryKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_DIARY_KEYWORDS_GEN")
    @SequenceGenerator(name = "SEQ_DIARY_KEYWORDS_GEN", sequenceName = "SEQ_DIARY_KEYWORDS", allocationSize = 1)
    @Column(name = "KEYWORD_ID")
    private Long keywordId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DIARY_ID", nullable = false)
    private TravelDiary travelDiary;

    @Enumerated(EnumType.STRING)
    @Column(name = "KEYWORD_TYPE", nullable = false, length = 20)
    private KeywordType keywordType;

    @Column(name = "KEYWORD_NAME", nullable = false, length = 50)
    private String keywordName;

    // JPA 연관관계 편의 메서드 - TravelDiary.addKeyword()에서 함께 호출됨
    public void assignDiary(TravelDiary travelDiary) {
        this.travelDiary = travelDiary;
    }
}
