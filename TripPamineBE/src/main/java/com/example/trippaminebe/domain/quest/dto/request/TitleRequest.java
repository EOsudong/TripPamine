package com.example.trippaminebe.domain.quest.dto.request;

import com.example.trippaminebe.domain.quest.entity.TitleConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// 칭호(TITLES) 등록/수정 요청 DTO (관리자용)
@Getter
@Setter
@Schema(description = "칭호 등록/수정 요청 DTO")
public class TitleRequest {

    @NotBlank(message = "칭호명을 입력해주세요.")
    @Schema(description = "칭호명", example = "여행 마스터")
    private String titleName;

    @Schema(description = "칭호 설명", example = "레벨 25 달성")
    private String description;

    @Schema(description = "칭호 아이콘 이미지 URL")
    private String iconUrl;

    @NotNull(message = "획득 조건 유형을 선택해주세요.")
    @Schema(description = "획득 조건 유형", example = "LEVEL_REACHED")
    private TitleConditionType requiredConditionType;

    // FIRST_QUEST_CLEAR 조건에서는 사용하지 않음(null 허용). QUEST_COUNT/LEVEL_REACHED는 필수지만
    // 서버에서 엄격히 막지는 않는다 - 비워두면 해당 조건은 사실상 만족 불가능한 칭호가 될 뿐이다.
    @Schema(description = "획득 조건 값 (QUEST_COUNT=누적 완료 수, LEVEL_REACHED=레벨). FIRST_QUEST_CLEAR는 미사용", example = "25")
    private Integer requiredConditionValue;
}
