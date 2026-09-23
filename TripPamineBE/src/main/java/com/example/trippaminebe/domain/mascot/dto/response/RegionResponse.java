package com.example.trippaminebe.domain.mascot.dto.response;

import com.example.trippaminebe.domain.mascot.entity.Region;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "지역 응답 DTO")
public class RegionResponse {

    private Long regionId;
    private String regionName;
    private String sidoCode;

    public static RegionResponse from(Region region) {
        return RegionResponse.builder()
                .regionId(region.getRegionId())
                .regionName(region.getRegionName())
                .sidoCode(region.getSidoCode())
                .build();
    }
}
