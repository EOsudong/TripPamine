package com.example.trippaminebe.domain.diary.controller;

import com.example.trippaminebe.domain.diary.dto.response.TravelDiaryResponse;
import com.example.trippaminebe.domain.diary.service.TravelDiaryService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// AI 여행 다이어리(TRAVEL_DIARIES + DIARY_KEYWORDS) 사용자 API
@RestController
@RequiredArgsConstructor
@RequestMapping("/diaries")
public class TravelDiaryController {

    private final TravelDiaryService travelDiaryService;

    private Long getUserId(CustomUserDetails userDetails) {
        return userDetails.getUser().getId();
    }

    @PostMapping("/generate")
    @Operation(summary = "여행 종료 후 AI 다이어리 생성")
    public ResponseEntity<TravelDiaryResponse> generate(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam Long planId
    ) {
        TravelDiaryResponse response = travelDiaryService.generateDiaryReport(planId, getUserId(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "내 AI 다이어리 목록 조회")
    public ResponseEntity<List<TravelDiaryResponse>> findAll(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(travelDiaryService.findAllByUserId(getUserId(userDetails)));
    }

    @GetMapping("/{diaryId}")
    @Operation(summary = "AI 다이어리 단건 조회")
    public ResponseEntity<TravelDiaryResponse> findById(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long diaryId
    ) {
        return ResponseEntity.ok(travelDiaryService.findById(diaryId, getUserId(userDetails)));
    }

    @PatchMapping("/{diaryId}/share")
    @Operation(summary = "다이어리 커뮤니티 공유 여부 토글")
    public ResponseEntity<TravelDiaryResponse> toggleShare(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long diaryId
    ) {
        return ResponseEntity.ok(travelDiaryService.toggleShare(diaryId, getUserId(userDetails)));
    }

    @DeleteMapping("/{diaryId}")
    @Operation(summary = "AI 다이어리 삭제")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long diaryId
    ) {
        travelDiaryService.delete(diaryId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }
}
