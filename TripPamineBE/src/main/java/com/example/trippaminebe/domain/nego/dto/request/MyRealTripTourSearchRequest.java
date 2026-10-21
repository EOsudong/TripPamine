package com.example.trippaminebe.domain.nego.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 검색 요청 필드 중 공개 문서로 확인된 keyword만 우선 사용한다. */
public record MyRealTripTourSearchRequest(
        @NotBlank(message = "검색어를 입력해주세요.")
        @Size(max = 80, message = "검색어는 최대 80자까지 입력할 수 있습니다.")
        String keyword
) {
}
