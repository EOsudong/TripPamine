package com.example.trippaminebe.domain.nego.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// AI_NEGO_LOGS: 마감 임박 실시간 할인 제안(AI 타임 네고 / 핫딜) 발송 및 전환 여부 기록
@Entity
@Table(name = "AI_NEGO_LOGS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiNegoLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_AI_NEGO_LOGS_GEN")
    @SequenceGenerator(name = "SEQ_AI_NEGO_LOGS_GEN", sequenceName = "SEQ_AI_NEGO_LOGS", allocationSize = 1)
    @Column(name = "NEGO_ID")
    private Long negoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Column(name = "ITEM_NAME", nullable = false, length = 100)
    private String itemName;

    @Column(name = "OFFERED_PRICE", nullable = false)
    private BigDecimal offeredPrice;

    @Column(name = "EXPIRED_AT", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "CONVERSION_YN", nullable = false, columnDefinition = "CHAR(1)")
    @Builder.Default
    private String conversionYn = "N";

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public boolean isConverted() {
        return "Y".equals(this.conversionYn);
    }

    // 카운트다운 만료 전 결제 완료 처리
    public void convert() {
        this.conversionYn = "Y";
    }
}
