package com.example.trippaminebe.domain.quest.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// USER_QUEST_STEP_LOGS: 사용자가 연쇄 퀘스트의 각 단계를 "클리어에 성공한" 기록만 남긴다.
// GPS 반경 밖이라 실패한 시도는 저장하지 않는다(그냥 다시 시도하면 됨) - UserTitle과 같이
// 성공 사실 자체가 불변 기록이므로 Setter를 두지 않는다.
@Entity
@Table(name = "USER_QUEST_STEP_LOGS")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuestStepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USER_QUEST_STEP_LOGS_GEN")
    @SequenceGenerator(name = "SEQ_USER_QUEST_STEP_LOGS_GEN", sequenceName = "SEQ_USER_QUEST_STEP_LOGS", allocationSize = 1)
    @Column(name = "USER_QUEST_STEP_LOG_ID")
    private Long userQuestStepLogId;

    // 연쇄 퀘스트 전체에 대한 USER_QUEST_LOGS 로우(퀘스트 시작 시 생성되는 그 로그)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LOG_ID", nullable = false)
    private UserQuestLog questLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "STEP_ID", nullable = false)
    private QuestStep step;

    @Column(name = "CLEARED_AT", insertable = false, updatable = false)
    private LocalDateTime clearedAt;

    @Column(name = "CURRENT_LAT", precision = 10, scale = 7)
    private BigDecimal currentLat;

    @Column(name = "CURRENT_LNG", precision = 11, scale = 7)
    private BigDecimal currentLng;
}
