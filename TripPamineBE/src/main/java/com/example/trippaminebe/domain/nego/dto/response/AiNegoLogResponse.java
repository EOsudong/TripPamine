package com.example.trippaminebe.domain.nego.dto.response;

import com.example.trippaminebe.domain.nego.entity.AiNegoLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "AI 타임 네고 / 실시간 핫딜 응답 DTO")
public class AiNegoLogResponse {

    private Long negoId;
    private String itemName;
    private BigDecimal offeredPrice;
    private LocalDateTime expiredAt;
    private String conversionYn;
    private long remainingSeconds; // 프론트 카운트다운 렌더링용 (음수면 이미 만료)

    public static AiNegoLogResponse from(AiNegoLog log) {
        long remaining = java.time.Duration.between(LocalDateTime.now(), log.getExpiredAt()).getSeconds();

        return AiNegoLogResponse.builder()
            .negoId(log.getNegoId())
            .itemName(log.getItemName())
            .offeredPrice(log.getOfferedPrice())
            .expiredAt(log.getExpiredAt())
            .conversionYn(log.getConversionYn())
            .remainingSeconds(remaining)
            .build();
    }
}
