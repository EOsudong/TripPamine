package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// CHAIN 내부 단계와 달리 서로 다른 QUESTS를 선행-후속 관계로 연결한다.
@Entity
@Table(name = "QUEST_PREREQUISITES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QuestPrerequisite {

    @EmbeddedId
    private QuestPrerequisiteId id;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public QuestPrerequisite(Long questId, Long prerequisiteQuestId) {
        this.id = new QuestPrerequisiteId(questId, prerequisiteQuestId);
    }
}
