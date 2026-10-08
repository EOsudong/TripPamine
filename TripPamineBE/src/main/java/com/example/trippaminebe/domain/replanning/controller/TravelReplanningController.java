package com.example.trippaminebe.domain.replanning.controller;

import com.example.trippaminebe.domain.replanning.dto.ReplanningRequest;
import com.example.trippaminebe.domain.replanning.dto.ReplanningResponse;
import com.example.trippaminebe.domain.replanning.service.TravelReplanningService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/travel-replanning")
@RequiredArgsConstructor
public class TravelReplanningController {
    private final TravelReplanningService service;

    @PostMapping("/{planId}/preview")
    public ReplanningResponse preview(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long planId,
            @RequestBody ReplanningRequest request) {
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return service.preview(planId, principal.getUser().getId(), request);
    }
    /**
     * AI 재계획 확정
     * POST /api/travel-replanning/{planId}/apply
     */
    @PostMapping("/{planId}/apply")
    public ResponseEntity<Map<String, String>> apply(
        @AuthenticationPrincipal CustomUserDetails principal,
        @PathVariable Long planId,
        @RequestBody Map<String, String> request
    ) {
        // 1. 로그인 확인
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "로그인이 필요합니다."
            );
        }

        // 2. previewId 확인
        String previewId = request.get("previewId");

        if (previewId == null || previewId.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "previewId가 필요합니다."
            );
        }

        // 3. 서버에 보관된 AI 재계획 결과 확정
        service.apply(
            planId,
            principal.getUser().getId(),
            previewId
        );

        return ResponseEntity.ok(
            Map.of("message", "AI 재계획 일정이 성공적으로 반영되었습니다.")
        );
    }
}
