package com.example.trippaminebe.domain.mascot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// POST /mascots/spawns/{spawnId}/encounter 응답.
// 여기서 발급된 encounterToken을 안드로이드 androidBridge.startArCatch(...) 호출 시 그대로 담아 보내고,
// 의사 AR 화면에서 포획 미니게임이 끝나면 그 토큰으로 /catch를 호출해서 최종 확정한다.
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "마스코트 조우(encounter) 응답 DTO")
public class MascotEncounterResponse {

    private String encounterToken;
    private int expiresInSeconds;

    private Long spawnId;
    private Long mascotId;
    private String mascotName;
    private String mascotRarity;
    private String mascotImageUrl;
    private Long rewardPoint;
}
