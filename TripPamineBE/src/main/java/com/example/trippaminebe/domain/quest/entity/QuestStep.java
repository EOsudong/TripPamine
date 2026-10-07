package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// QUEST_STEPS: 연쇄(CHAIN) 퀘스트의 단계별 세부 목표. RPG 확장 기획서 - 연쇄 퀘스트 섹션.
// 하나의 QUESTS(questType=CHAIN) 로우가 여러 QUEST_STEPS를 가지며, 사용자는 STEP_ORDER
// 순서대로 각 단계의 목표 좌표에 도달해야 다음 단계로 진행할 수 있다.
@Entity
@Table(name = "QUEST_STEPS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestStep {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_QUEST_STEPS_GEN")
    @SequenceGenerator(name = "SEQ_QUEST_STEPS_GEN", sequenceName = "SEQ_QUEST_STEPS", allocationSize = 1)
    @Column(name = "STEP_ID")
    private Long stepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QUEST_ID", nullable = false)
    private Quest quest;

    // 1부터 시작. 같은 QUEST_ID 안에서 유일해야 한다(UQ_QUEST_STEPS_ORDER).
    @Column(name = "STEP_ORDER", nullable = false)
    @Builder.Default
    private Integer stepOrder = 1;

    @Column(name = "STEP_NAME", nullable = false, length = 100)
    private String stepName;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "TARGET_LAT", nullable = false, precision = 10, scale = 7)
    private BigDecimal targetLat;

    @Column(name = "TARGET_LNG", nullable = false, precision = 11, scale = 7)
    private BigDecimal targetLng;

    @Column(name = "CLEAR_RADIUS", nullable = false)
    @Builder.Default
    private Integer clearRadius = Quest.DEFAULT_CLEAR_RADIUS_METERS;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // 단계 정보 수정(관리자용) 도메인 메서드
    public void update(Integer stepOrder, String stepName, String description,
                        BigDecimal targetLat, BigDecimal targetLng, Integer clearRadius) {
        this.stepOrder = stepOrder != null ? stepOrder : this.stepOrder;
        this.stepName = stepName;
        this.description = description;
        this.targetLat = targetLat;
        this.targetLng = targetLng;
        this.clearRadius = clearRadius != null ? clearRadius : this.clearRadius;
        this.updatedAt = LocalDateTime.now();
    }
}
