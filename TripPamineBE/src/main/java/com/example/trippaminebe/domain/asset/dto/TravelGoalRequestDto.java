package com.example.trippaminebe.domain.asset.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class TravelGoalRequestDto {

    // =========================================
    // 여행 목표 이름
    // =========================================
    // 예:
    // 일본 도쿄 여행
    // 유럽 배낭여행
    // 제주도 한달살기
    // =========================================

    @NotBlank(message = "여행 목표 이름을 입력해주세요.")
    private String goalName;


    // =========================================
    // 여행에 필요한 목표 금액
    // =========================================

    @NotNull(message = "목표 금액을 입력해주세요.")
    @Min(value = 1, message = "목표 금액은 1원 이상이어야 합니다.")
    private Long targetAmount;


    // =========================================
    // 현재까지 모은 여행자금
    // =========================================

    @NotNull(message = "현재 모은 금액을 입력해주세요.")
    @Min(value = 0, message = "현재 금액은 0원 이상이어야 합니다.")
    private Long currentAmount;


    // =========================================
    // 여행 목표 날짜
    // =========================================

    @NotNull(message = "여행 목표 날짜를 입력해주세요.")
    @Future(message = "여행 목표 날짜는 오늘 이후여야 합니다.")
    private LocalDate targetDate;
}