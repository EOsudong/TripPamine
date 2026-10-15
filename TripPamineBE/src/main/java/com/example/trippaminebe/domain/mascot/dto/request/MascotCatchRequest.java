package com.example.trippaminebe.domain.mascot.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// 포획 확정 요청 DTO - 웹 미니게임 또는 안드로이드 의사 AR 화면(ArCatchActivity)에서
// 포획 미니게임(탭/타이밍)이 끝난 뒤 호출한다.
//
// [변경] success(boolean) → score(0~100).
// 클라이언트가 성공/실패를 정하지 않고 미니게임 점수만 보낸다. 실제 포획 여부는 서버(MascotCatchJudge)가
// 희귀도 + 점수로 확률을 계산해서 판정한다.
@Getter
@Setter
@Schema(description = "마스코트 포획 확정 요청 DTO")
public class MascotCatchRequest {

    @NotBlank(message = "encounterToken이 필요합니다. 먼저 encounter API를 호출해주세요.")
    @Schema(description = "encounter API로 발급받은 단기 토큰")
    private String encounterToken;

    @Min(value = 0, message = "점수는 0 이상이어야 합니다.")
    @Max(value = 100, message = "점수는 100 이하여야 합니다.")
    @Schema(description = "포획 미니게임 점수(0~100). 높을수록 포획 확률이 올라간다.", example = "80")
    private int score;
}
