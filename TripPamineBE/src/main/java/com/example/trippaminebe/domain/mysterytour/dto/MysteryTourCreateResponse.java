package com.example.trippaminebe.domain.mysterytour.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class MysteryTourCreateResponse {

    private Long mysteryTourId;

    private LocalDate travelDate;

    private Integer travelDays;

    private Integer peopleCount;

    private Long budget;

    private Integer questCount;

    private String status;

    private boolean destinationLocked;
}