package com.example.trippaminebe.domain.mascot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 마스코트 스폰 지점 등록/수정 요청 DTO (관리자용)
// quest.dto.request.QuestRequest와 같은 뼈대. targetLat/Lng는 카카오 로컬 API(mysterytour 도메인)로
// 실제 관광지 좌표를 미리 확정해서 채워 넣는 걸 권장한다.
@Getter
@Setter
@Schema(description = "마스코트 스폰 지점 등록/수정 요청 DTO")
public class MascotSpawnPointRequest {

    @NotNull(message = "마스코트(mascotId)를 입력해주세요.")
    @Schema(description = "마스코트 ID", example = "1")
    private Long mascotId;

    @Schema(description = "장소명(표시용)", example = "해운대 해수욕장")
    private String spotName;

    @NotNull(message = "타겟 위도를 입력해주세요.")
    @Schema(description = "타겟 위도", example = "35.1587000")
    private BigDecimal targetLat;

    @NotNull(message = "타겟 경도를 입력해주세요.")
    @Schema(description = "타겟 경도", example = "129.1604000")
    private BigDecimal targetLng;

    @Min(value = 1, message = "포획 인정 반경은 1m 이상이어야 합니다.")
    @Schema(description = "포획 인정 반경(m). 미입력 시 100m", example = "150")
    private Integer catchRadius;

    @Schema(description = "이 퀘스트를 클리어해야 스폰되도록 연동할 퀘스트 ID(선택)")
    private Long linkedQuestId;

    @Schema(description = "스폰 활성 여부(Y/N). 미입력 시 Y", example = "Y")
    private String activeYn;
}
