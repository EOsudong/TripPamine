package com.example.trippaminebe.domain.quest.controller;

import com.example.trippaminebe.domain.quest.dto.request.TitleRequest;
import com.example.trippaminebe.domain.quest.dto.response.TitleResponse;
import com.example.trippaminebe.domain.quest.service.TitleService;
import com.example.trippaminebe.security.jwt.aspect.AdminLoggable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 칭호(TITLES) 관리자 CRUD. AdminQuestChapterController와 동일한 패턴.
@RestController
@RequestMapping("/admin/titles")
@RequiredArgsConstructor
public class AdminTitleController {

    private final TitleService titleService;

    @GetMapping
    @Operation(summary = "칭호 목록 조회 (관리자용)")
    public ResponseEntity<List<TitleResponse>> getTitleList() {
        return ResponseEntity.ok(titleService.findAll());
    }

    @PostMapping
    @Operation(summary = "칭호 등록 (관리자용)")
    @AdminLoggable(actionType = "TITLE_CREATE", targetTable = "TITLES")
    public ResponseEntity<TitleResponse> createTitle(@Valid @RequestBody TitleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(titleService.create(request));
    }

    @PutMapping("/{titleId}")
    @Operation(summary = "칭호 수정 (관리자용)")
    @AdminLoggable(actionType = "TITLE_UPDATE", targetTable = "TITLES")
    public ResponseEntity<TitleResponse> updateTitle(
        @PathVariable Long titleId,
        @Valid @RequestBody TitleRequest request
    ) {
        return ResponseEntity.ok(titleService.update(titleId, request));
    }

    @DeleteMapping("/{titleId}")
    @Operation(summary = "칭호 삭제 (관리자용)")
    @AdminLoggable(actionType = "TITLE_DELETE", targetTable = "TITLES")
    public ResponseEntity<Void> deleteTitle(@PathVariable Long titleId) {
        titleService.delete(titleId);
        return ResponseEntity.noContent().build();
    }
}
