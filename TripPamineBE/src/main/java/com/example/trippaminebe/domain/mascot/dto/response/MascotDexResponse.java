package com.example.trippaminebe.domain.mascot.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

// GET /mascots/dex 응답 - 도감 화면 전용.
// GET /mascots/collection(내가 잡은 기록 목록)과 달리 "전체 마스코트"를 지역별로 내려주고,
// 각 마스코트에 내 수집 여부/횟수를 붙인다. 프론트는 collected=false인 마스코트를 실루엣으로 그린다.
@Schema(description = "마스코트 도감 응답 DTO")
public record MascotDexResponse(
        int totalCount,
        int collectedCount,
        @Schema(description = "전체 수집률(%) - 소수점 첫째 자리")
        double completionRate,
        List<RegionDex> regions
) {

    public record RegionDex(
            Long regionId,
            String regionName,
            int totalCount,
            int collectedCount,
            List<DexEntry> mascots
    ) {
    }

    public record DexEntry(
            Long mascotId,
            String mascotName,
            String rarity,
            String rarityLabel,
            String imageUrl,
            Long rewardPoint,
            boolean collected,
            long catchCount,
            LocalDateTime firstCaughtAt,
            LocalDateTime lastCaughtAt
    ) {
    }

    public static double rate(int collected, int total) {
        if (total == 0) {
            return 0.0;
        }
        return Math.round(collected * 1000.0 / total) / 10.0;
    }
}
