package com.example.trippaminebe.domain.replanning.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReplanningResponse(
    Long planId,
    BigDecimal totalBudget,
    BigDecimal spentAmount,
    BigDecimal remainingBudget,
    Map<String, Object> proposal,
    String previewId,
    List<String> originalPlaces
) {}