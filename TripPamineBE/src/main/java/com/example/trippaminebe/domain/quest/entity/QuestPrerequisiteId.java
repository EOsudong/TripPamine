package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// QUEST_PREREQUISITES 복합 PK: 후속 퀘스트와 선행 퀘스트의 고유한 한 쌍
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class QuestPrerequisiteId implements Serializable {

    @Column(name = "QUEST_ID", nullable = false)
    private Long questId;

    @Column(name = "PREREQUISITE_QUEST_ID", nullable = false)
    private Long prerequisiteQuestId;
}
