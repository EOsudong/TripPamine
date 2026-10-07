package com.example.trippaminebe.domain.quest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 연쇄 퀘스트 단계(QUEST_STEPS) 등록/수정 요청 DTO (관리자용)
@Getter
@Setter
@Schema(description = "연쇄 퀘스트 단계 등록/수정 요청 DTO")
public class QuestStepRequest {

    // 미입력 시: 등록은 서버가 "현재 등록된 단계 수 + 1"로 자동 배정, 수정은 기존 값 유지
    @Schema(description = "단계 순서(1부터). 미입력 시 등록은 자동 배정, 수정은 기존 값 유지", example = "1")
    private Integer stepOrder;

    @NotBlank(message = "단계명을 입력해주세요.")
    @Schema(description = "단계명", example = "1단계. 광화문 앞에서 사진 찍기")
    private String stepName;

    @Schema(description = "단계 설명")
    private String description;

    @NotNull(message = "목표 위도를 입력해주세요.")
    @Schema(description = "목표 위도", example = "37.5759360")
    private BigDecimal targetLat;

    @NotNull(message = "목표 경도를 입력해주세요.")
    @Schema(description = "목표 경도", example = "126.9768150")
    private BigDecimal targetLng;

    @Schema(description = "클리어 인정 반경(m). 미입력 시 100m", example = "100")
    private Integer clearRadius;
}
