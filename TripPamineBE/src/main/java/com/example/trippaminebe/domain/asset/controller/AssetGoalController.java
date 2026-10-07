package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.AssetGoalRequestDto;
import com.example.trippaminebe.domain.asset.dto.AssetGoalResponseDto;
import com.example.trippaminebe.domain.asset.service.AssetGoalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assets/goals")
@RequiredArgsConstructor
public class AssetGoalController {

    private final AssetGoalService assetGoalService;


    // ========================================
    // 목표 등록
    // POST /api/assets/goals
    // ========================================
    @PostMapping
    public ResponseEntity<Long> register(
            @RequestBody AssetGoalRequestDto requestDto
    ) {

        Long goalId =
                assetGoalService.register(requestDto);

        return ResponseEntity.ok(goalId);
    }


    // ========================================
    // 전체 목표 조회
    // GET /api/assets/goals
    // ========================================
    @GetMapping
    public ResponseEntity<List<AssetGoalResponseDto>> getAll() {

        List<AssetGoalResponseDto> goals =
                assetGoalService.getAll();

        return ResponseEntity.ok(goals);
    }


    // ========================================
    // 특정 목표 조회
    // GET /api/assets/goals/{goalId}
    // ========================================
    @GetMapping("/{goalId}")
    public ResponseEntity<AssetGoalResponseDto> getOne(
            @PathVariable Long goalId
    ) {

        AssetGoalResponseDto goal =
                assetGoalService.getOne(goalId);

        return ResponseEntity.ok(goal);
    }


    // ========================================
    // 목표 수정
    // PUT /api/assets/goals/{goalId}
    // ========================================
    @PutMapping("/{goalId}")
    public ResponseEntity<Void> modify(
            @PathVariable Long goalId,
            @RequestBody AssetGoalRequestDto requestDto
    ) {

        assetGoalService.modify(
                goalId,
                requestDto
        );

        return ResponseEntity.noContent().build();
    }


    // ========================================
    // 목표 삭제
    // DELETE /api/assets/goals/{goalId}
    // ========================================
    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long goalId
    ) {

        assetGoalService.remove(goalId);

        return ResponseEntity.noContent().build();
    }
}