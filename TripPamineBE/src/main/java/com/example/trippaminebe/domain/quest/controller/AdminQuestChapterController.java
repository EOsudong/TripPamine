package com.example.trippaminebe.domain.quest.controller;

import com.example.trippaminebe.domain.quest.dto.request.QuestChapterRequest;
import com.example.trippaminebe.domain.quest.dto.response.QuestChapterResponse;
import com.example.trippaminebe.domain.quest.service.QuestChapterService;
import com.example.trippaminebe.security.jwt.aspect.AdminLoggable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 퀘스트 챕터(QUEST_CHAPTERS) 관리자 CRUD. AdminQuestController와 동일한 패턴.
@RestController
@RequestMapping("/admin/quests/chapters")
@RequiredArgsConstructor
public class AdminQuestChapterController {

    private final QuestChapterService questChapterService;

    @GetMapping
    @Operation(summary = "퀘스트 챕터 목록 조회 (관리자용)")
    public ResponseEntity<List<QuestChapterResponse>> getChapterList() {
        return ResponseEntity.ok(questChapterService.findAll(null));
    }

    @PostMapping
    @Operation(summary = "퀘스트 챕터 등록 (관리자용)")
    @AdminLoggable(actionType = "QUEST_CHAPTER_CREATE", targetTable = "QUEST_CHAPTERS")
    public ResponseEntity<QuestChapterResponse> createChapter(@Valid @RequestBody QuestChapterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questChapterService.create(request));
    }

    @PutMapping("/{chapterId}")
    @Operation(summary = "퀘스트 챕터 수정 (관리자용)")
    @AdminLoggable(actionType = "QUEST_CHAPTER_UPDATE", targetTable = "QUEST_CHAPTERS")
    public ResponseEntity<QuestChapterResponse> updateChapter(
        @PathVariable Long chapterId,
        @Valid @RequestBody QuestChapterRequest request
    ) {
        return ResponseEntity.ok(questChapterService.update(chapterId, request));
    }

    @DeleteMapping("/{chapterId}")
    @Operation(summary = "퀘스트 챕터 삭제 (관리자용)")
    @AdminLoggable(actionType = "QUEST_CHAPTER_DELETE", targetTable = "QUEST_CHAPTERS")
    public ResponseEntity<Void> deleteChapter(@PathVariable Long chapterId) {
        questChapterService.delete(chapterId);
        return ResponseEntity.noContent().build();
    }
}
