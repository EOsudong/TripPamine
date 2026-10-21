package com.example.trippaminebe.domain.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 상품 검색 응답의 gid/productUrl로만 제휴 링크를 생성한다. */
public record MyRealTripMylinkRequest(
        @NotBlank(message = "상품 ID를 입력해주세요.")
        @Pattern(regexp = "[0-9]{1,20}", message = "올바른 상품 ID가 아닙니다.")
        String gid,
        @NotBlank(message = "상품 URL을 입력해주세요.")
        @Size(max = 1000, message = "상품 URL이 너무 깁니다.")
        String productUrl
) {
}
