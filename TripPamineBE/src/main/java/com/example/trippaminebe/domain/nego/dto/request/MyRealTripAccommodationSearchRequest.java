package com.example.trippaminebe.domain.nego.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 숙소 검색은 투어 검색과 달리 체크인·체크아웃 및 성인 인원을 함께 전달한다. */
public record MyRealTripAccommodationSearchRequest(
        @NotBlank(message = "숙소를 검색할 지역 또는 숙소명을 입력해주세요.")
        @Size(max = 80, message = "검색어는 80자 이하로 입력해주세요.")
        String keyword,

        @NotBlank(message = "체크인 날짜를 선택해주세요.")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "체크인은 YYYY-MM-DD 형식이어야 합니다.")
        String checkIn,

        @NotBlank(message = "체크아웃 날짜를 선택해주세요.")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "체크아웃은 YYYY-MM-DD 형식이어야 합니다.")
        String checkOut,

        @NotNull(message = "성인 인원을 입력해주세요.")
        @Min(value = 1, message = "성인은 최소 1명 이상이어야 합니다.")
        @Max(value = 20, message = "한 번에 검색할 성인 인원은 최대 20명입니다.")
        Integer adultCount,

        // 숙소 검색의 첫 페이지는 0. 기존 검색 호출은 page 생략을 허용한다.
        @Min(value = 0, message = "페이지는 0 이상이어야 합니다.")
        @Max(value = 199, message = "한 번에 조회할 수 있는 숙소 페이지는 최대 200페이지입니다.")
        Integer page
) {
}
