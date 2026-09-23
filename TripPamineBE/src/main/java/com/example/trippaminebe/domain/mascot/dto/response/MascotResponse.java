package com.example.trippaminebe.domain.mascot.dto.response;

import com.example.trippaminebe.domain.mascot.entity.Mascot;
import com.example.trippaminebe.domain.mascot.entity.MascotRarity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "마스코트 마스터 응답 DTO")
public class MascotResponse {

    private Long mascotId;
    private Long regionId;
    private String regionName;
    private String mascotName;
    private MascotRarity rarity;
    private String imageUrl;
    private String modelUrl;
    private Long rewardPoint;

    public static MascotResponse from(Mascot mascot) {
        return MascotResponse.builder()
                .mascotId(mascot.getMascotId())
                .regionId(mascot.getRegion().getRegionId())
                .regionName(mascot.getRegion().getRegionName())
                .mascotName(mascot.getMascotName())
                .rarity(mascot.getRarity())
                .imageUrl(mascot.getImageUrl())
                .modelUrl(mascot.getModelUrl())
                .rewardPoint(mascot.getRewardPoint())
                .build();
    }
}
