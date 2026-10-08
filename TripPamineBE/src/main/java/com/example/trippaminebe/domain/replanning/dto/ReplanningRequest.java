package com.example.trippaminebe.domain.replanning.dto;

import java.util.List;

public record ReplanningRequest(
        List<String> remainingPlaces,
        String currentLocation,
        String weather,
        Integer delayMinutes
) {}
