package com.example.trippaminebe.domain.quest.controller;

import com.example.trippaminebe.domain.quest.dto.request.QuestClearRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestChapterResponse;
import com.example.trippaminebe.domain.quest.dto.response.QuestResponse;
import com.example.trippaminebe.domain.quest.dto.response.QuestStepClearResponse;
import com.example.trippaminebe.domain.quest.dto.response.QuestStepResponse;
import com.example.trippaminebe.domain.quest.dto.response.RpgStatsResponse;
import com.example.trippaminebe.domain.quest.dto.response.TitleResponse;
import com.example.trippaminebe.domain.quest.dto.response.UserQuestLogResponse;
import com.example.trippaminebe.domain.quest.service.QuestChapterService;
import com.example.trippaminebe.domain.quest.service.QuestService;
import com.example.trippaminebe.domain.quest.service.RpgStatsService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 위치 기반 실시간 미션(QUESTS) 사용자 API
@RestController
@RequiredArgsConstructor
@RequestMapping("/quests")
public class QuestController {

    private final QuestService questService;
    private final RpgStatsService rpgStatsService;
    private final QuestChapterService questChapterService;

    private Long getUserId(CustomUserDetails userDetails) {
        return userDetails.getUser().getId();
    }

    @GetMapping
    @Operation(summary = "퀘스트 목록 조회 (활성 퀘스트만, 내 레벨 기준 잠금 여부 포함)")
    public ResponseEntity<List<QuestResponse>> findAll(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(questService.findAllForUser(getUserId(userDetails)));
    }

    @GetMapping("/{questId}")
    @Operation(summary = "퀘스트 단건 조회")
    public ResponseEntity<QuestResponse> findById(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long questId
    ) {
        return ResponseEntity.ok(questService.findByIdForUser(questId, getUserId(userDetails)));
    }

    @GetMapping("/my-logs")
    @Operation(summary = "내 퀘스트 수행 이력 조회")
    public ResponseEntity<List<UserQuestLogResponse>> findMyLogs(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(questService.findMyLogs(getUserId(userDetails)));
    }

    @PostMapping("/{questId}/start")
    @Operation(summary = "퀘스트 시작")
    public ResponseEntity<UserQuestLogResponse> start(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long questId
    ) {
        return ResponseEntity.ok(questService.start(getUserId(userDetails), questId));
    }

    @PostMapping("/{questId}/clear")
    @Operation(summary = "퀘스트 클리어(GPS 반경 인증)")
    public ResponseEntity<UserQuestLogResponse> clear(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long questId,
        @Valid @RequestBody QuestClearRequest request
    ) {
        return ResponseEntity.ok(questService.clear(getUserId(userDetails), questId, request));
    }

    @GetMapping("/{questId}/steps")
    @Operation(summary = "연쇄 퀘스트 단계 목록 조회 (내 클리어 여부 포함)")
    public ResponseEntity<List<QuestStepResponse>> findSteps(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long questId
    ) {
        return ResponseEntity.ok(questService.findStepsForUser(questId, getUserId(userDetails)));
    }

    @PostMapping("/{questId}/steps/{stepId}/clear")
    @Operation(summary = "연쇄 퀘스트 단계 클리어(GPS 반경 인증) - 순서대로만 진행 가능")
    public ResponseEntity<QuestStepClearResponse> clearStep(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long questId,
        @PathVariable Long stepId,
        @Valid @RequestBody QuestClearRequest request
    ) {
        return ResponseEntity.ok(questService.clearStep(getUserId(userDetails), questId, stepId, request));
    }

    @GetMapping("/chapters")
    @Operation(summary = "퀘스트 챕터 목록 조회 (내 레벨 기준 잠금 여부 포함)")
    public ResponseEntity<List<QuestChapterResponse>> findChapters(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Integer viewerLevel = rpgStatsService.findMyLevel(getUserId(userDetails));
        return ResponseEntity.ok(questChapterService.findAll(viewerLevel));
    }

    @GetMapping("/me/stats")
    @Operation(summary = "내 RPG 스탯 조회 (레벨/EXP/완료 퀘스트 수 등)")
    public ResponseEntity<RpgStatsResponse> findMyStats(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(rpgStatsService.findMyStats(getUserId(userDetails)));
    }

    @GetMapping("/me/titles")
    @Operation(summary = "내 칭호 목록 조회 (전체 칭호 + 보유/장착 여부)")
    public ResponseEntity<List<TitleResponse>> findMyTitles(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(rpgStatsService.findMyTitles(getUserId(userDetails)));
    }

    // [칭호 변경] 보유한 칭호 장착
    @PutMapping("/me/titles/{titleId}/equip")
    @Operation(summary = "내 칭호 장착 (보유한 칭호만 가능)")
    public ResponseEntity<RpgStatsResponse> equipTitle(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long titleId
    ) {
        return ResponseEntity.ok(rpgStatsService.equipTitle(getUserId(userDetails), titleId));
    }

    // [칭호 변경] 칭호 해제 (칭호 없이 표시)
    @DeleteMapping("/me/titles/equip")
    @Operation(summary = "내 칭호 해제")
    public ResponseEntity<RpgStatsResponse> unequipTitle(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(rpgStatsService.unequipTitle(getUserId(userDetails)));
    }
}