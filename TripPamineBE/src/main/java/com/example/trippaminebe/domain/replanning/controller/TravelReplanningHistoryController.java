package com.example.trippaminebe.domain.replanning.controller;

import com.example.trippaminebe.domain.replanning.entity.TravelReplanningHistory;
import com.example.trippaminebe.domain.replanning.repository.TravelReplanningHistoryRepository;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.example.trippaminebe.domain.replanning.service.TravelReplanningRestoreService;
import com.example.trippaminebe.domain.travel.repository.TravelPlanRepository;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/travel-plans")
@RequiredArgsConstructor
public class TravelReplanningHistoryController {

    private final TravelReplanningHistoryRepository historyRepository;
    private final TravelReplanningRestoreService restoreService;
    private final TravelPlanRepository travelPlanRepository;

    /**
     * AI 여행 재계획 변경 이력 조회
     * GET /api/travel-plans/{planId}/replanning/history
     */
    @GetMapping("/{planId}/replanning/history")
    public ResponseEntity<?> getReplanningHistory(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long planId
    ) {

        // 1. 로그인 확인
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        // 2. 로그인 사용자 ID
        Long userId = principal.getUser().getId();

        // 로그인 사용자의 여행 계획 소유권 검증
        travelPlanRepository.findByPlanIdAndUser_IdAndDelYn(
                planId,
                userId,
                "N"
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "여행 계획을 찾을 수 없거나 접근 권한이 없습니다."
        ));

        // 3. 해당 사용자의 여행 변경 이력만 조회
        List<TravelReplanningHistory> histories =
                historyRepository.findByPlanIdAndUserIdOrderByCreatedAtDesc(
                        planId,
                        userId
                );

        // 4. 응답 데이터 구성
        List<Map<String, Object>> result = histories.stream()
                .map(history -> Map.<String, Object>of(
                        "historyId", history.getHistoryId(),
                        "planId", history.getPlanId(),
                        "targetDay", history.getTargetDay(),
                        "reason", history.getReason() == null
                                ? ""
                                : history.getReason(),
                        "createdAt", history.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }
    /**
     * AI 여행 재계획 변경 이력 복구
     * POST /api/travel-plans/{planId}/replanning/history/{historyId}/restore
     */
    @PostMapping("/{planId}/replanning/history/{historyId}/restore")
    public ResponseEntity<Map<String, String>> restoreReplanningHistory(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long planId,
            @PathVariable Long historyId
    ) {

        // 1. 로그인 확인
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        // 2. 로그인 사용자 ID
        Long userId = principal.getUser().getId();

        // 3. 이전 일정 복구
        restoreService.restore(planId, historyId, userId);

        // 4. 성공 응답
        return ResponseEntity.ok(
                Map.of("message", "이전 여행 일정으로 복구되었습니다.")
        );
    }
}