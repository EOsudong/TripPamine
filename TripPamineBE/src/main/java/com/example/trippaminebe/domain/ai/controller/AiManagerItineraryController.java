package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.TravelItinerarySnapshot;
import com.example.trippaminebe.domain.ai.service.TravelItineraryContextService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * STEP 7 확인용 읽기 전용 일정 Context API.
 * GET /api/ai-manager/itineraries/{planId}?resolvePlaces=true
 * 기본 호출은 외부 POI 검색 없이 저장된 방문 순서/시간만 반환한다.
 */
@RestController
@RequestMapping("/api/ai-manager/itineraries")
@RequiredArgsConstructor
public class AiManagerItineraryController {

    private final TravelItineraryContextService itineraryContextService;

    @GetMapping("/{planId}")
    public TravelItinerarySnapshot getItinerary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long planId,
            @RequestParam(defaultValue = "false") boolean resolvePlaces
    ) {
        if (userDetails == null || userDetails.getUser() == null
                || userDetails.getUser().getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Long userId = userDetails.getUser().getId();
        TravelItinerarySnapshot snapshot = itineraryContextService.load(userId, planId);
        return resolvePlaces ? itineraryContextService.withCoordinates(snapshot) : snapshot;
    }
}
