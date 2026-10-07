package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// TITLES: 자동 획득형 칭호 마스터.
@Entity
@Table(name = "TITLES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_TITLES_GEN")
    @SequenceGenerator(name = "SEQ_TITLES_GEN", sequenceName = "SEQ_TITLES", allocationSize = 1)
    @Column(name = "TITLE_ID")
    private Long titleId;

    @Column(name = "TITLE_NAME", nullable = false, length = 50)
    private String titleName;

    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    @Column(name = "ICON_URL", length = 500)
    private String iconUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "REQUIRED_CONDITION_TYPE", nullable = false, length = 30)
    private TitleConditionType requiredConditionType;

    // FIRST_QUEST_CLEAR 조건에서는 사용하지 않음(null 허용)
    @Column(name = "REQUIRED_CONDITION_VALUE")
    private Integer requiredConditionValue;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // 현재 스탯이 이 칭호의 획득 조건을 만족하는지 평가
    public boolean isSatisfiedBy(int level, int completedQuests) {
        return switch (requiredConditionType) {
            case FIRST_QUEST_CLEAR -> completedQuests >= 1;
            case QUEST_COUNT -> requiredConditionValue != null && completedQuests >= requiredConditionValue;
            case LEVEL_REACHED -> requiredConditionValue != null && level >= requiredConditionValue;
        };
    }

    // 칭호 정보 수정(관리자용) 도메인 메서드
    public void update(String titleName, String description, String iconUrl,
                        TitleConditionType requiredConditionType, Integer requiredConditionValue) {
        this.titleName = titleName;
        this.description = description;
        this.iconUrl = iconUrl;
        this.requiredConditionType = requiredConditionType;
        this.requiredConditionValue = requiredConditionValue;
        this.updatedAt = LocalDateTime.now();
    }
}
