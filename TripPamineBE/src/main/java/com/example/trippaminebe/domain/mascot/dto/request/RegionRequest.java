package com.example.trippaminebe.domain.mascot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 지역 등록/수정 요청 DTO (관리자용)
@Getter
@Setter
@Schema(description = "지역 등록/수정 요청 DTO")
public class RegionRequest {

    @NotBlank(message = "지역명을 입력해주세요.")
    @Schema(description = "지역명", example = "부산광역시 해운대구")
    private String regionName;

    @Schema(description = "행정구역 코드(선택)", example = "26350")
    private String sidoCode;
}
