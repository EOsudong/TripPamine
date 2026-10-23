package com.example.trippaminebe.domain.quest.controller;

import com.example.trippaminebe.domain.quest.dto.request.QuestRequest;
import com.example.trippaminebe.domain.quest.dto.request.QuestPrerequisiteRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestPrerequisiteResponse;
import com.example.trippaminebe.domain.quest.service.QuestPrerequisiteService;
import com.example.trippaminebe.domain.quest.dto.response.QuestResponse;
import com.example.trippaminebe.domain.quest.service.QuestService;
import com.example.trippaminebe.security.jwt.aspect.AdminLoggable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/quests")
@RequiredArgsConstructor
public class AdminQuestController {

    private final QuestService questService;
    private final QuestPrerequisiteService questPrerequisiteService;

    @GetMapping
    @Operation(summary = "퀘스트 목록 조회 (관리자용)")
    public ResponseEntity<List<QuestResponse>> getQuestList() {
        return ResponseEntity.ok(questService.findAll());
    }

    @PostMapping
    @Operation(summary = "퀘스트 등록 (관리자용)")
    @AdminLoggable(actionType = "QUEST_CREATE", targetTable = "QUESTS")
    public ResponseEntity<QuestResponse> createQuest(@Valid @RequestBody QuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questService.create(request));
    }

    @PutMapping("/{questId}")
    @Operation(summary = "퀘스트 수정 (관리자용)")
    @AdminLoggable(actionType = "QUEST_UPDATE", targetTable = "QUESTS")
    public ResponseEntity<QuestResponse> updateQuest(
        @PathVariable Long questId,
        @Valid @RequestBody QuestRequest request
    ) {
        return ResponseEntity.ok(questService.update(questId, request));
    }

    @GetMapping("/{questId}/prerequisites")
    @Operation(summary = "선행 퀘스트 목록 조회 (관리자용)")
    public ResponseEntity<List<QuestPrerequisiteResponse>> getPrerequisites(@PathVariable Long questId) {
        return ResponseEntity.ok(questPrerequisiteService.findAll(questId));
    }

    @PostMapping("/{questId}/prerequisites")
    @Operation(summary = "독립 선행 퀘스트 연결 (AND 규칙, 순환 검증)")
    @AdminLoggable(actionType = "QUEST_PREREQUISITE_ADD", targetTable = "QUEST_PREREQUISITES")
    public ResponseEntity<List<QuestPrerequisiteResponse>> addPrerequisite(
            @PathVariable Long questId, @Valid @RequestBody QuestPrerequisiteRequest request) {
        return ResponseEntity.ok(questPrerequisiteService.add(questId, request.getPrerequisiteQuestId()));
    }

    @DeleteMapping("/{questId}/prerequisites/{prerequisiteQuestId}")
    @Operation(summary = "선행 퀘스트 연결 해제")
    @AdminLoggable(actionType = "QUEST_PREREQUISITE_REMOVE", targetTable = "QUEST_PREREQUISITES")
    public ResponseEntity<Void> removePrerequisite(
            @PathVariable Long questId, @PathVariable Long prerequisiteQuestId) {
        questPrerequisiteService.remove(questId, prerequisiteQuestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{questId}")
    @Operation(summary = "퀘스트 삭제 (관리자용)")
    @AdminLoggable(actionType = "QUEST_DELETE", targetTable = "QUESTS")
    public ResponseEntity<Void> deleteQuest(@PathVariable Long questId) {
        questService.delete(questId);
        return ResponseEntity.noContent().build();
    }
}
