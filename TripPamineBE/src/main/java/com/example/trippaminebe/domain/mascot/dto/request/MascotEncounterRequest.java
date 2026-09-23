package com.example.trippaminebe.domain.mascot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 마스코트 조우(encounter) 요청 DTO - quest.dto.request.QuestClearRequest와 완전히 동일한 뼈대.
// "지금 이 위치에서 이 스폰 포인트를 조우할 수 있는지" 서버에 물어보는 첫 번째 검증 단계.
@Getter
@Setter
@Schema(description = "마스코트 조우(encounter) 요청 DTO (현재 위치)")
public class MascotEncounterRequest {

    @NotNull(message = "현재 위도를 전달해주세요.")
    @Schema(description = "현재 위도", example = "35.1587000")
    private BigDecimal currentLat;

    @NotNull(message = "현재 경도를 전달해주세요.")
    @Schema(description = "현재 경도", example = "129.1604000")
    private BigDecimal currentLng;

    // quest.dto.request.QuestClearRequest.accuracyMeters와 동일한 이유로 존재.
    // 값이 없으면 정확도 검증은 건너뛰고 거리 검증만 수행한다.
    @Schema(description = "GPS 오차 반경(Horizontal Accuracy, m) - 선택값", example = "12.0")
    private Double accuracyMeters;
}
