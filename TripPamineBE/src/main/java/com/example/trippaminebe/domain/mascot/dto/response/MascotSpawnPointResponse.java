package com.example.trippaminebe.domain.mascot.dto.response;

import com.example.trippaminebe.domain.mascot.entity.MascotSpawnPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

// 지도(MascotHuntMap.tsx)에 마커로 뿌릴 스폰 지점 응답 DTO.
// quest.dto.response.QuestResponse와 비슷하지만, 지도에서 바로 마스코트 이미지/이름을 보여줄 수 있도록
// Mascot 정보를 한 번 평탄화(flatten)해서 함께 내려준다 - 프론트에서 추가 조회 없이 렌더링 가능하게.
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "마스코트 스폰 지점 응답 DTO (지도 표시용)")
public class MascotSpawnPointResponse {

    private Long spawnId;
    private String spotName;
    private BigDecimal targetLat;
    private BigDecimal targetLng;
    private Integer catchRadius;

    private Long mascotId;
    private String mascotName;
    private String mascotRarity;
    private String mascotImageUrl;

    // 이미 이 스폰 지점에서 이 마스코트를 잡은 적이 있는지 (도감 중복 표시용, 재포획 허용 여부와 무관하게 UI 참고용)
    private boolean alreadyCaught;

    public static MascotSpawnPointResponse from(MascotSpawnPoint spawnPoint, boolean alreadyCaught) {
        return MascotSpawnPointResponse.builder()
                .spawnId(spawnPoint.getSpawnId())
                .spotName(spawnPoint.getSpotName())
                .targetLat(spawnPoint.getTargetLat())
                .targetLng(spawnPoint.getTargetLng())
                .catchRadius(spawnPoint.getCatchRadius())
                .mascotId(spawnPoint.getMascot().getMascotId())
                .mascotName(spawnPoint.getMascot().getMascotName())
                .mascotRarity(spawnPoint.getMascot().getRarity().name())
                .mascotImageUrl(spawnPoint.getMascot().getImageUrl())
                .alreadyCaught(alreadyCaught)
                .build();
    }
}
