package com.example.trippaminebe.domain.mascot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// POST /mascots/spawns/{spawnId}/catch 응답
//
// [재포획 정책] 같은 마스코트는 몇 번이든 다시 잡을 수 있지만, 포인트는 "첫 포획"에만 지급한다.
// 프론트는 firstCatch로 "NEW! 도감 등록 +300P" / "다시 만났어요 (N번째)" 연출을 구분한다.
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "마스코트 포획 확정 응답 DTO")
public class MascotCatchResponse {

    @Schema(description = "이번 시도로 포획에 성공했는지")
    private boolean caught;

    @Schema(description = "도감에 처음 등록된 포획인지 (true일 때만 포인트 지급)")
    private boolean firstCatch;

    @Schema(description = "이 마스코트를 지금까지 잡은 총 횟수 (이번 포획 포함)")
    private long catchCount;

    @Schema(description = "실패 시 같은 조우로 남은 재도전 횟수. 0이면 다시 조우부터 해야 함")
    private int remainingAttempts;

    private Long mascotId;
    private String mascotName;

    @Schema(description = "이번에 지급된 포인트 (실패 또는 재포획이면 0)")
    private Long rewardPoint;

    @Schema(description = "지급 이후 유저의 전체 보유 포인트")
    private Long totalPoints;
}
