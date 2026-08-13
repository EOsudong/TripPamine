package com.example.trippaminebe.domain.travel.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TravelPlanRequest {

    @NotBlank(message = "여행 이름은 필수입니다.")
    private String planName;

    @Min(value = 0, message = "예산은 0원 이상이어야 합니다.")
    private BigDecimal totalBudget;

    private String companionType;

    private String blindYn;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startDate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endDate;
}