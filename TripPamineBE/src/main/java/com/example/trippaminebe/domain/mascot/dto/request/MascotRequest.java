package com.example.trippaminebe.domain.mascot.dto.request;

import com.example.trippaminebe.domain.mascot.entity.MascotRarity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// 마스코트 등록/수정 요청 DTO (관리자용)
@Getter
@Setter
@Schema(description = "마스코트 등록/수정 요청 DTO")
public class MascotRequest {

    @NotNull(message = "소속 지역(regionId)을 입력해주세요.")
    @Schema(description = "소속 지역 ID", example = "1")
    private Long regionId;

    @NotBlank(message = "마스코트명을 입력해주세요.")
    @Schema(description = "마스코트명", example = "해운대 갈매기 파도리")
    private String mascotName;

    @Schema(description = "희귀도. 미입력 시 COMMON", example = "RARE")
    private MascotRarity rarity;

    @Schema(description = "의사 AR 카메라 화면에 겹칠 2D 스프라이트(투명 배경 PNG) URL")
    private String imageUrl;

    @Schema(description = "Phase 3(ARCore) 3D 모델 URL - 지금은 비워둬도 됨")
    private String modelUrl;

    @Schema(description = "포획 성공 보상 포인트", example = "300")
    private Long rewardPoint;
}
