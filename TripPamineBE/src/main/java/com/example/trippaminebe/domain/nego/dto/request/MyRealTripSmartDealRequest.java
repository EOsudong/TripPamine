package com.example.trippaminebe.domain.nego.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 내 여행 기반 제휴 상품 추천 요청. 가격은 상품 검색 결과의 표시 시작가 기준이다. */
public record MyRealTripSmartDealRequest(
        @NotNull(message = "여행 계획을 선택해주세요.")
        @Positive(message = "올바른 여행 계획 ID가 아닙니다.")
        Long planId,

        @NotBlank(message = "여행 지역 또는 상품 검색어를 입력해주세요.")
        @Size(max = 80, message = "검색어는 80자를 넘을 수 없습니다.")
        String keyword,

        @DecimalMin(value = "0.01", message = "상품 가격 상한은 0원보다 커야 합니다.")
        BigDecimal maxProductPrice
) {
}
