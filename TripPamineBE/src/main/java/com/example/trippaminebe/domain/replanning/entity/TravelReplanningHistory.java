package com.example.trippaminebe.domain.replanning.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "AI_TRAVEL_REPLANNING_HISTORY")
@Getter
@Setter
@NoArgsConstructor
public class TravelReplanningHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HISTORY_ID")
    private Long historyId;

    @Column(name = "PLAN_ID", nullable = false)
    private Long planId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "TARGET_DAY", nullable = false)
    private Integer targetDay;

    @Lob
    @Column(name = "ORIGINAL_JSON", nullable = false)
    private String originalJson;

    @Lob
    @Column(name = "REPLANNED_JSON", nullable = false)
    private String replannedJson;

    @Column(name = "REASON", length = 2000)
    private String reason;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}