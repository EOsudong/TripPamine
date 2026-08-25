package com.example.trippaminebe.domain.mysterytour.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class MysteryTourCreateRequest {

    private LocalDate travelDate;

    private Integer travelDays;

    private Integer peopleCount;

    private Long budget;

    private Integer radiusKm;

    private String departure;

    private String travelStyle;
}