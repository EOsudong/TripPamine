package com.example.trippaminebe.domain.quest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// 퀘스트 클리어(GPS 반경 인증) 요청 DTO - 클라이언트가 실시간으로 수집한 현재 위치
@Getter
@Setter
@Schema(description = "퀘스트 클리어 요청 DTO (현재 위치)")
public class QuestClearRequest {

    @NotNull(message = "현재 위도를 전달해주세요.")
    @Schema(description = "현재 위도", example = "37.5665300")
    private BigDecimal currentLat;

    @NotNull(message = "현재 경도를 전달해주세요.")
    @Schema(description = "현재 경도", example = "126.9780400")
    private BigDecimal currentLng;

    // 선택값: 모바일 GPS의 Horizontal Accuracy(오차 반경, m). 클라이언트에서만 필터링하면
    // 모킹 위치 앱으로 우회 가능하므로 서버에서도 한 번 더 신뢰도를 검증하기 위해 받음.
    // 값이 없으면(구버전 클라이언트 등) 정확도 검증은 건너뛰고 거리 검증만 수행.
    @Schema(description = "GPS 오차 반경(Horizontal Accuracy, m) - 선택값", example = "15.0")
    private Double accuracyMeters;
}
