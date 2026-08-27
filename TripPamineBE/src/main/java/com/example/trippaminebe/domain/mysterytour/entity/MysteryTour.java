package com.example.trippaminebe.domain.mysterytour.entity;

import com.example.trippaminebe.domain.mysterytour.exception.MysteryTourStateConflictException;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "MYSTERY_TOURS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MysteryTour {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "mystery_tour_seq"
    )
    @SequenceGenerator(
            name = "mystery_tour_seq",
            sequenceName = "SEQ_MYSTERY_TOURS",
            allocationSize = 1
    )
    @Column(name = "MYSTERY_TOUR_ID")
    private Long mysteryTourId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "TRAVEL_DATE", nullable = false)
    private LocalDate travelDate;

    @Column(name = "TRAVEL_DAYS", nullable = false)
    private Integer travelDays;

    @Column(name = "PEOPLE_COUNT", nullable = false)
    private Integer peopleCount;

    @Column(name = "BUDGET", nullable = false)
    private Long budget;

    @Column(name = "RADIUS_KM", nullable = false)
    private Integer radiusKm;

    @Column(name = "DEPARTURE", nullable = false, length = 200)
    private String departure;

    @Column(name = "TRAVEL_STYLE", nullable = false, length = 30)
    private String travelStyle;

    @Column(name = "DESTINATION", length = 200)
    private String destination;

    @Lob
    @Column(name = "AI_PLAN_JSON")
    private String aiPlanJson;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MysteryProgressStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column(name = "ABANDONED_AT")
    private LocalDateTime abandonedAt;

    @OneToMany(
            mappedBy = "mysteryTour",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<MysteryQuest> quests = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = MysteryProgressStatus.READY;
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void start() {
        if (status != MysteryProgressStatus.READY) {
            throw new MysteryTourStateConflictException("준비 상태의 미스터리 투어만 시작할 수 있습니다.");
        }

        status = MysteryProgressStatus.PROGRESS;
        startedAt = LocalDateTime.now();
    }

    public void complete() {
        if (status != MysteryProgressStatus.PROGRESS) {
            throw new MysteryTourStateConflictException("진행 중인 미스터리 투어만 완료할 수 있습니다.");
        }

        status = MysteryProgressStatus.SUCCESS;
        completedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status != MysteryProgressStatus.READY) {
            throw new MysteryTourStateConflictException("이미 시작된 미스터리 투어는 취소할 수 없습니다.");
        }

        status = MysteryProgressStatus.CANCELLED;
    }

    public void abandon() {
        if (status != MysteryProgressStatus.PROGRESS) {
            throw new MysteryTourStateConflictException("진행 중인 미스터리 투어만 포기할 수 있습니다.");
        }

        status = MysteryProgressStatus.ABANDONED;
        abandonedAt = LocalDateTime.now();
    }
}
