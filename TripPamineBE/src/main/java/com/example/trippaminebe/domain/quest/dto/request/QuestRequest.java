package com.example.trippaminebe.domain.quest.dto.request;

import com.example.trippaminebe.domain.quest.entity.QuestType;
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

    @Schema(description = "퀘스트 설명(현장 미션 스토리 텍스트)", example = "남산 어딘가에 숨겨진 여행자의 흔적을 찾아라.")
    private String description;

    @NotNull(message = "타겟 위도를 입력해주세요.")
    @Schema(description = "타겟 위도", example = "37.5665000")
    private BigDecimal targetLat;

    @NotNull(message = "타겟 경도를 입력해주세요.")
    @Schema(description = "타겟 경도", example = "126.9780000")
    private BigDecimal targetLng;

    @Min(value = 0, message = "보상은 음수일 수 없습니다.")
    @Schema(description = "성공 보상 포인트", example = "500")
    private Long rewardPoint;

    @Min(value = 0, message = "보상은 음수일 수 없습니다.")
    @Schema(description = "성공 보상 EXP. 미입력 시 0", example = "300")
    private Long rewardExp;

    // 장소 특성에 따라 관리자가 개별 지정 (미입력 시 서비스 기본값 100m 적용)
    @Min(value = 1, message = "클리어 반경은 1m 이상이어야 합니다.")
    @Schema(description = "클리어 인정 반경(m). 미입력 시 100m", example = "300")
    private Integer clearRadius;

    @Schema(description = "퀘스트 유형. 미입력 시 LOCATION", example = "PHOTO")
    private QuestType questType;

    @Min(value = 1, message = "난이도는 1 이상이어야 합니다.")
    @Schema(description = "난이도(1~3 권장). 미입력 시 1", example = "2")
    private Integer difficulty;

    @Schema(description = "대표 이미지 URL")
    private String imageUrl;

    @Schema(description = "목록용 썸네일 URL")
    private String thumbnailUrl;

    @Schema(description = "소속 챕터 ID. 미입력 시 미분류")
    private Long chapterId;

    @Min(value = 1, message = "필요 레벨은 1 이상이어야 합니다.")
    @Schema(description = "시작 가능한 최소 레벨. 미입력 시 1", example = "5")
    private Integer requiredLevel;

    @Schema(description = "사용자 노출 여부. 미입력 시 true(노출)", example = "true")
    private Boolean isActive;
}
