package com.example.trippaminebe.domain.quest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 퀘스트 등록/수정 요청 DTO (관리자용)
@Getter
@Setter
@Schema(description = "퀘스트 등록/수정 요청 DTO")
public class QuestRequest {

    @NotBlank(message = "퀘스트명을 입력해주세요.")
    @Schema(description = "퀘스트명", example = "로컬 마켓 인증")
    private String questName;

    @NotNull(message = "타겟 위도를 입력해주세요.")
    @Schema(description = "타겟 위도", example = "37.5665000")
    private BigDecimal targetLat;

    @NotNull(message = "타겟 경도를 입력해주세요.")
    @Schema(description = "타겟 경도", example = "126.9780000")
    private BigDecimal targetLng;

    @Schema(description = "성공 보상 포인트", example = "500")
    private Long rewardPoint;

    // 장소 특성에 따라 관리자가 개별 지정 (미입력 시 서비스 기본값 100m 적용)
    @Min(value = 1, message = "클리어 반경은 1m 이상이어야 합니다.")
    @Schema(description = "클리어 인정 반경(m). 미입력 시 100m", example = "300")
    private Integer clearRadius;
}
