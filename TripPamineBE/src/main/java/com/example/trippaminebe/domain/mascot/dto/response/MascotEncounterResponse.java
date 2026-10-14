package com.example.trippaminebe.domain.mascot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// POST /mascots/spawns/{spawnId}/encounter 응답.
// 여기서 발급된 encounterToken을 포획 화면(웹 미니게임 또는 androidBridge.startArCatch(...))에 넘기고,
// 미니게임이 끝나면 그 토큰 + 점수로 /catch를 호출해서 최종 확정한다.
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "마스코트 조우(encounter) 응답 DTO")
public class MascotEncounterResponse {
    private String encounterToken;
    private int expiresInSeconds;

    @Schema(description = "이 토큰으로 포획을 시도할 수 있는 최대 횟수")
    private int maxAttempts;

    private Long spawnId;
    private Long mascotId;
    private String mascotName;
    private String mascotRarity;
    private String mascotImageUrl;

    @Schema(description = "마스코트의 기본 보상 포인트 (첫 포획일 때만 실제 지급)")
    private Long rewardPoint;

    @Schema(description = "이미 도감에 있는 마스코트인지. true면 잡아도 포인트가 없다고 미리 안내")
    private boolean alreadyCollected;
}
