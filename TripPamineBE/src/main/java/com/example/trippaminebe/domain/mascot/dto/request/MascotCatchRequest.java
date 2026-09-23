package com.example.trippaminebe.domain.mascot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 포획 최종 확정 요청 DTO - 안드로이드 의사 AR 화면(ArCatchActivity)에서
// 포획 미니게임(탭/드래그) 결과가 나온 뒤 호출한다.
@Getter
@Setter
@Schema(description = "마스코트 포획 확정 요청 DTO")
public class MascotCatchRequest {

    @NotBlank(message = "encounterToken이 필요합니다. 먼저 encounter API를 호출해주세요.")
    @Schema(description = "encounter API로 발급받은 단기 토큰")
    private String encounterToken;

    // 클라이언트(의사 AR 미니게임)에서의 포획 성공/실패 결과.
    // false로 오면 서버는 도감에 기록하지 않고, 재도전 가능하도록 토큰도 소모하지 않는다.
    @Schema(description = "포획 미니게임 성공 여부", example = "true")
    private boolean success;
}
