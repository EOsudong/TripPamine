package com.example.trippaminebe.domain.quest.controller;

import com.example.trippaminebe.domain.quest.dto.request.QuestStepRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestStepResponse;
import com.example.trippaminebe.domain.quest.service.QuestStepService;
import com.example.trippaminebe.security.jwt.aspect.AdminLoggable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 연쇄 퀘스트 단계(QUEST_STEPS) 관리자 CRUD. AdminQuestChapterController와 동일한 패턴.
// 퀘스트에 종속된 리소스이므로 questId를 경로에 포함한다.
@RestController
@RequestMapping("/admin/quests/{questId}/steps")
@RequiredArgsConstructor
public class AdminQuestStepController {

    private final QuestStepService questStepService;

    @GetMapping
    @Operation(summary = "연쇄 퀘스트 단계 목록 조회 (관리자용)")
    public ResponseEntity<List<QuestStepResponse>> getStepList(@PathVariable Long questId) {
        return ResponseEntity.ok(questStepService.findAllByQuest(questId));
    }

    @PostMapping
    @Operation(summary = "연쇄 퀘스트 단계 등록 (관리자용)")
    @AdminLoggable(actionType = "QUEST_STEP_CREATE", targetTable = "QUEST_STEPS")
    public ResponseEntity<QuestStepResponse> createStep(
        @PathVariable Long questId,
        @Valid @RequestBody QuestStepRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questStepService.create(questId, request));
    }

    @PutMapping("/{stepId}")
    @Operation(summary = "연쇄 퀘스트 단계 수정 (관리자용)")
    @AdminLoggable(actionType = "QUEST_STEP_UPDATE", targetTable = "QUEST_STEPS")
    public ResponseEntity<QuestStepResponse> updateStep(
        @PathVariable Long questId,
        @PathVariable Long stepId,
        @Valid @RequestBody QuestStepRequest request
    ) {
        return ResponseEntity.ok(questStepService.update(questId, stepId, request));
    }

    @DeleteMapping("/{stepId}")
    @Operation(summary = "연쇄 퀘스트 단계 삭제 (관리자용)")
    @AdminLoggable(actionType = "QUEST_STEP_DELETE", targetTable = "QUEST_STEPS")
    public ResponseEntity<Void> deleteStep(
        @PathVariable Long questId,
        @PathVariable Long stepId
    ) {
        questStepService.delete(questId, stepId);
        return ResponseEntity.noContent().build();
    }
}
