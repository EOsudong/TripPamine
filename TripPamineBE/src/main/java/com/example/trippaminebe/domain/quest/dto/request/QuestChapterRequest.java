package com.example.trippaminebe.domain.quest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 퀘스트 챕터 등록/수정 요청 DTO (관리자용)
@Getter
@Setter
@Schema(description = "퀘스트 챕터 등록/수정 요청 DTO")
public class QuestChapterRequest {

    @NotBlank(message = "챕터명을 입력해주세요.")
    @Schema(description = "챕터명", example = "서울의 첫 번째 발자국")
    private String chapterName;

    @Schema(description = "챕터 설명")
    private String description;

    @Schema(description = "지역", example = "서울")
    private String region;

    @Schema(description = "정렬 순서. 미입력 시 0", example = "1")
    private Integer orderNo;

    @Schema(description = "챕터 진입 최소 레벨. 미입력 시 1", example = "1")
    private Integer requiredLevel;

    @Schema(description = "챕터 대표 이미지 URL")
    private String imageUrl;
}
