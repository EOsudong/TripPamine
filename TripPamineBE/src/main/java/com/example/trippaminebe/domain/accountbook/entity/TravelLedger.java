package com.example.trippaminebe.domain.accountbook.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// TRAVEL_LEDGERS: 여행 가계부 지출 내역.
// - ID: NUMBER 시퀀스가 아니라 VARCHAR2(50) 문자열 PK라 UUID를 애플리케이션에서 직접 채번함
// - ROUTE_ITEM_ID: ROUTE_DETAILS 엔티티가 아직 코드베이스에 없어 연관관계 대신 단순 컬럼(Long)으로만 매핑
// - CATEGORY_ID(코드, 2자)와 CATEGORY_NM(표시명)이 분리되어 있어 둘 다 매핑
// - SPENT_TIME은 TIMESTAMP가 아니라 VARCHAR2(8) (예: "14:30:00" 같은 시각 문자열로)
@Entity
@Table(name = "TRAVEL_LEDGERS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TravelLedger {

    @Id
    @Column(name = "ID", length = 50)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PLAN_ID", nullable = false)
    private TravelPlan travelPlan;

    // ROUTE_DETAILS 엔티티 미구현 상태라 연관관계 매핑 대신 FK 값만 보관
    @Column(name = "ROUTE_ITEM_ID")
    private Long routeItemId;

    @Column(name = "AMOUNT")
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "PAYMENT", length = 50)
    private String payment;

    @Column(name = "CATEGORY_ID", length = 2)
    private String categoryId;

    @Column(name = "CATEGORY_NM", length = 50)
    private String categoryNm;

    @Column(name = "MEMO", length = 255)
    private String memo;

    @Column(name = "SPENT_TIME", length = 8)
    private String spentTime; // "HH:mm:ss" 형태의 시각 문자열로 - 실제 포맷은 프론트/기록 로직 확인

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
