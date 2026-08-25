package com.example.trippaminebe.domain.quest.controller;

import com.example.trippaminebe.domain.quest.dto.request.QuestRequest;
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

// TripPamineAdmin README의 "아직 없는 기능 - 퀘스트 관리"에 해당하는 관리자 전용 CRUD API
@RestController
@RequestMapping("/admin/quests")
@RequiredArgsConstructor
public class AdminQuestController {

    private final QuestService questService;

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

    @DeleteMapping("/{questId}")
    @Operation(summary = "퀘스트 삭제 (관리자용)")
    @AdminLoggable(actionType = "QUEST_DELETE", targetTable = "QUESTS")
    public ResponseEntity<Void> deleteQuest(@PathVariable Long questId) {
        questService.delete(questId);
        return ResponseEntity.noContent().build();
    }
}
