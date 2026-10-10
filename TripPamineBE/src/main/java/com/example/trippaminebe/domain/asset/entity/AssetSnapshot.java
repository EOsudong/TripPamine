package com.example.trippaminebe.domain.asset.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
// [수정] 사용자당 하루 1개 Snapshot을 DB 차원에서 보장한다.
// (ddl-auto: validate 는 제약조건을 검사하지 않으므로 실제 제약은
//  resources/db/asset_snapshot_unique.sql 을 DB에 한 번 실행해서 추가한다)
@Table(
        name = "ASSET_SNAPSHOTS",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_ASSET_SNAPSHOTS_USER_DATE",
                columnNames = {"USER_ID", "SNAPSHOT_DATE"}
        )
)
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
