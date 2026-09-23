package com.example.trippaminebe.domain.mascot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// POST /mascots/spawns/{spawnId}/catch 응답
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "마스코트 포획 확정 응답 DTO")
public class MascotCatchResponse {

    private boolean caught;
    private Long mascotId;
    private String mascotName;
    private Long rewardPoint; // 이번에 지급된 포인트 (실패면 0)
    private Long totalPoints; // 지급 이후 유저의 전체 보유 포인트
}
