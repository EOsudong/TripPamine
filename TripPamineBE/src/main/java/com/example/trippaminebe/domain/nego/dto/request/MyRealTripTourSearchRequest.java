package com.example.trippaminebe.domain.nego.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 검색 요청 필드 중 공개 문서로 확인된 keyword만 우선 사용한다. */
public record MyRealTripTourSearchRequest(
        @NotBlank(message = "검색어를 입력해주세요.")
        @Size(max = 80, message = "검색어는 최대 80자까지 입력할 수 있습니다.")
        String keyword,

        // 투어·티켓의 첫 페이지는 1. 기존 호출은 page 생략을 허용한다.
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.")
        @Max(value = 200, message = "한 번에 조회할 수 있는 페이지는 최대 200입니다.")
        Integer page
) {
}