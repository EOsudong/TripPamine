package com.example.trippaminebe.domain.quest.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// USER_QUEST_LOGS: 사용자별 퀘스트 진행/성공/실패 추적 로그
@Entity
@Table(name = "USER_QUEST_LOGS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_USER_QUEST_LOGS_GEN")
    @SequenceGenerator(name = "SEQ_USER_QUEST_LOGS_GEN", sequenceName = "SEQ_USER_QUEST_LOGS", allocationSize = 1)
    @Column(name = "LOG_ID")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "QUEST_ID", nullable = false)
    private Quest quest;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    @Builder.Default
    private QuestStatus status = QuestStatus.PROGRESS;

    @Column(name = "CLEAR_DATE")
    private LocalDateTime clearDate;

    // GPS 반경 검증 성공 시 호출 - 클리어 처리 도메인 메서드
    public void success() {
        this.status = QuestStatus.SUCCESS;
        this.clearDate = LocalDateTime.now();
    }

    // GPS 반경 밖이거나 조건 불충족 시 실패 처리 도메인 메서드
    public void fail() {
        this.status = QuestStatus.FAILED;
        this.clearDate = LocalDateTime.now();
    }
}
