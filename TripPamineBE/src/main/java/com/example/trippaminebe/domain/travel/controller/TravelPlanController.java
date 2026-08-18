package com.example.trippaminebe.domain.travel.controller;

import com.example.trippaminebe.domain.travel.dto.TravelPlanRequest;
import com.example.trippaminebe.domain.travel.dto.TravelPlanResponse;
import com.example.trippaminebe.domain.travel.service.TravelPlanService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/travel-plans")
public class TravelPlanController {

    private final TravelPlanService travelPlanService;

    // NPE 방지 헬퍼 메서드
    private Long getUserIdSafely(CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.getUser() != null) {
            return userDetails.getUser().getId();
        }
        return 10L; // 로그인 정보가 없을 시 임시 사용할 DB USERS ID (test1@trippamine.com)
    }

    @PostMapping
    @Operation(summary = "여행 계획 등록")
    public ResponseEntity<TravelPlanResponse> create(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody TravelPlanRequest request
    ) {
        TravelPlanResponse response = travelPlanService.create(getUserIdSafely(userDetails), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "내 여행 계획 목록 조회")
    public ResponseEntity<List<TravelPlanResponse>> findAll(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
            travelPlanService.findAllByUserId(getUserIdSafely(userDetails))
        );
    }

    @GetMapping("/{planId}")
    @Operation(summary = "여행 계획 단건 조회")
    public ResponseEntity<TravelPlanResponse> findById(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long planId
    ) {
        return ResponseEntity.ok(
            travelPlanService.findById(planId, getUserIdSafely(userDetails))
        );
    }

    @PutMapping("/{planId}")
    @Operation(summary = "여행 계획 수정")
    public ResponseEntity<TravelPlanResponse> update(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long planId,
        @Valid @RequestBody TravelPlanRequest request
    ) {
        return ResponseEntity.ok(
            travelPlanService.update(planId, getUserIdSafely(userDetails), request)
        );
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "여행 계획 삭제")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long planId
    ) {
        travelPlanService.delete(planId, getUserIdSafely(userDetails));
        return ResponseEntity.noContent().build();
    }
}