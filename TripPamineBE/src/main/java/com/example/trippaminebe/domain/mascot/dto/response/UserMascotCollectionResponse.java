package com.example.trippaminebe.domain.mascot.dto.response;

import com.example.trippaminebe.domain.mascot.entity.UserMascotCollection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// GET /mascots/collection 응답 - 마이페이지/도감 화면에서 쓰는 "내가 잡은 마스코트" 한 건
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "내 마스코트 도감(수집 기록) 응답 DTO")
public class UserMascotCollectionResponse {

    private Long collectionId;
    private Long mascotId;
    private String mascotName;
    private String mascotRarity;
    private String mascotImageUrl;
    private String regionName;
    private LocalDateTime caughtAt;

    public static UserMascotCollectionResponse from(UserMascotCollection collection) {
        return UserMascotCollectionResponse.builder()
                .collectionId(collection.getCollectionId())
                .mascotId(collection.getMascot().getMascotId())
                .mascotName(collection.getMascot().getMascotName())
                .mascotRarity(collection.getMascot().getRarity().name())
                .mascotImageUrl(collection.getMascot().getImageUrl())
                .regionName(collection.getMascot().getRegion().getRegionName())
                .caughtAt(collection.getCaughtAt())
                .build();
    }
}
