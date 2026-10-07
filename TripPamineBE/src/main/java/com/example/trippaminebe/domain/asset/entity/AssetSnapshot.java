package com.example.trippaminebe.domain.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ASSET_SNAPSHOTS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetSnapshot {

    @Id
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "asset_snapshot_seq"
    )
    @SequenceGenerator(
            name = "asset_snapshot_seq",
            sequenceName = "SEQ_ASSET_SNAPSHOTS",
            allocationSize = 1
    )
    @Column(name = "SNAPSHOT_ID")
    private Long snapshotId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "SNAPSHOT_DATE", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "TOTAL_ASSET", nullable = false)
    private Long totalAsset;

    @Column(name = "TOTAL_INCOME", nullable = false)
    private Long totalIncome;

    @Column(name = "TOTAL_EXPENSE", nullable = false)
    private Long totalExpense;

    @Column(name = "TRAVEL_EXPENSE", nullable = false)
    private Long travelExpense;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {

        if (snapshotDate == null) {
            snapshotDate = LocalDate.now();
        }

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}