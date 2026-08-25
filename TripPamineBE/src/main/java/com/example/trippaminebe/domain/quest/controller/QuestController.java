package com.example.trippaminebe.domain.quest.controller;

import com.example.trippaminebe.domain.quest.dto.request.QuestClearRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestResponse;
import com.example.trippaminebe.domain.quest.dto.response.UserQuestLogResponse;
import com.example.trippaminebe.domain.quest.service.QuestService;
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

    private Long getUserId(CustomUserDetails userDetails) {
        return userDetails.getUser().getId();
    }

    @GetMapping
    @Operation(summary = "퀘스트 목록 조회")
    public ResponseEntity<List<QuestResponse>> findAll() {
        return ResponseEntity.ok(questService.findAll());
    }

    @GetMapping("/{questId}")
    @Operation(summary = "퀘스트 단건 조회")
    public ResponseEntity<QuestResponse> findById(@PathVariable Long questId) {
        return ResponseEntity.ok(questService.findById(questId));
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
}
