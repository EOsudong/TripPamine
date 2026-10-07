package com.example.trippaminebe.domain.quest.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// USER_RPG_STATS: 사용자별 RPG 레벨/경험치/퀘스트 통계. User와 1:1(PK 공유).
// USERS.TOTAL_POINTS/USER_GRADE(등급제)와는 별개의 "퀘스트 전용 레벨링 시스템"이다 - 기존 포인트/등급 로직은 그대로 둔다.
@Entity
@Table(name = "USER_RPG_STATS")
@Getter
@NoArgsConstructor
public class UserRpgStats {

    @Id
    @Column(name = "USER_ID")
    private Long userId;

    // PK를 USER와 공유(@MapsId) - USER_RPG_STATS.USER_ID = USERS.USER_ID
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "USER_ID")
    private User user;

    @Column(name = "USER_LEVEL", nullable = false)
    private Integer level;

    @Column(name = "USER_EXP", nullable = false)
    private Integer exp;

    @Column(name = "TOTAL_EXP", nullable = false)
    private Integer totalExp;

    @Column(name = "TOTAL_QUESTS", nullable = false)
    private Integer totalQuests;

    @Column(name = "COMPLETED_QUESTS", nullable = false)
    private Integer completedQuests;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CURRENT_TITLE_ID")
    private Title currentTitle;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // 사용자의 첫 퀘스트 시작/완료 시점에 지연 생성(lazy init)하기 위한 팩토리 메서드.
    // UserRpgStatsRepository에 해당 User의 row가 없을 때만 호출한다.
    public static UserRpgStats createDefault(User user) {
        UserRpgStats stats = new UserRpgStats();
        stats.user = user;
        stats.level = 1;
        stats.exp = 0;
        stats.totalExp = 0;
        stats.totalQuests = 0;
        stats.completedQuests = 0;
        return stats;
    }

    // 퀘스트를 "시작"할 때 호출 - 총 도전 횟수 집계
    public void increaseTotalQuests() {
        this.totalQuests = this.totalQuests + 1;
        this.updatedAt = LocalDateTime.now();
    }

    // 퀘스트 "클리어" 성공 시 호출 - 완료 횟수 집계 + 레벨업 적용은 호출부(RpgStatsService)에서
    // LevelCurve를 통해 계산한 뒤 applyLevelUp()으로 반영한다.
    public void increaseCompletedQuests() {
        this.completedQuests = this.completedQuests + 1;
        this.updatedAt = LocalDateTime.now();
    }

    public void applyLevelUp(int newLevel, int remainingExp, int gainedExp) {
        this.level = newLevel;
        this.exp = remainingExp;
        this.totalExp = this.totalExp + gainedExp;
        this.updatedAt = LocalDateTime.now();
    }

    public void equipTitle(Title title) {
        this.currentTitle = title;
        this.updatedAt = LocalDateTime.now();
    }
}
