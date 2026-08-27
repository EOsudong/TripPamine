package com.example.trippaminebe.domain.mysterytour.entity;

import com.example.trippaminebe.domain.mysterytour.exception.MysteryTourStateConflictException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "MYSTERY_QUESTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MysteryQuest {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "mystery_quest_seq"
    )
    @SequenceGenerator(
            name = "mystery_quest_seq",
            sequenceName = "SEQ_MYSTERY_QUESTS",
            allocationSize = 1
    )
    @Column(name = "MYSTERY_QUEST_ID")
    private Long mysteryQuestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MYSTERY_TOUR_ID", nullable = false)
    private MysteryTour mysteryTour;

    @Column(name = "QUEST_ORDER", nullable = false)
    private Integer questOrder;

    @Column(name = "QUEST_NAME", nullable = false, length = 150)
    private String questName;

    @Column(name = "QUEST_DESC", nullable = false, length = 2000)
    private String questDesc;

    @Column(name = "VERIFY_TYPE", nullable = false, length = 20)
    private String verifyType;

    @Column(name = "TARGET_LAT", precision = 10, scale = 7)
    private BigDecimal targetLat;

    @Column(name = "TARGET_LNG", precision = 10, scale = 7)
    private BigDecimal targetLng;

    @Column(name = "TIME_LIMIT_MIN")
    private Integer timeLimitMin;

    @Column(name = "REWARD_POINT", nullable = false)
    private Integer rewardPoint;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MysteryProgressStatus status;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column(name = "SKIPPED_AT")
    private LocalDateTime skippedAt;

    @PrePersist
    protected void onCreate() {
        if (rewardPoint == null) {
            rewardPoint = 0;
        }

        if (status == null) {
            status = MysteryProgressStatus.LOCKED;
        }
    }

    public void start() {
        if (status != MysteryProgressStatus.LOCKED) {
            throw new MysteryTourStateConflictException("잠금 상태의 퀘스트만 시작할 수 있습니다.");
        }

        status = MysteryProgressStatus.PROGRESS;
    }

    public void complete() {
        requireInProgress();
        status = MysteryProgressStatus.SUCCESS;
        completedAt = LocalDateTime.now();
    }

    public void skip() {
        requireInProgress();
        status = MysteryProgressStatus.SKIPPED;
        skippedAt = LocalDateTime.now();
    }

    public void abandon() {
        if (
                status == MysteryProgressStatus.LOCKED ||
                        status == MysteryProgressStatus.PROGRESS
        ) {
            status = MysteryProgressStatus.ABANDONED;
        }
    }

    private void requireInProgress() {
        if (status != MysteryProgressStatus.PROGRESS) {
            throw new MysteryTourStateConflictException("현재 진행 중인 퀘스트만 처리할 수 있습니다.");
        }
    }
}
