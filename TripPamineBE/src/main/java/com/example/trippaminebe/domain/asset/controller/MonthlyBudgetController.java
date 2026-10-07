package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetRequestDto;
import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetResponseDto;
import com.example.trippaminebe.domain.asset.service.MonthlyBudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assets/budgets")
@RequiredArgsConstructor
public class MonthlyBudgetController {

    private final MonthlyBudgetService monthlyBudgetService;


    // ========================================
    // 월별 예산 등록
    // POST /api/assets/budgets
    // ========================================
    @PostMapping
    public ResponseEntity<Long> register(
            @RequestBody MonthlyBudgetRequestDto requestDto
    ) {

        Long budgetId =
                monthlyBudgetService.register(requestDto);

        return ResponseEntity.ok(budgetId);
    }


    // ========================================
    // 전체 예산 조회
    // GET /api/assets/budgets
    // ========================================
    @GetMapping
    public ResponseEntity<List<MonthlyBudgetResponseDto>> getAll() {

        List<MonthlyBudgetResponseDto> budgets =
                monthlyBudgetService.getAll();

        return ResponseEntity.ok(budgets);
    }


    // ========================================
    // 특정 예산 조회
    // GET /api/assets/budgets/{budgetId}
    // ========================================
    @GetMapping("/{budgetId}")
    public ResponseEntity<MonthlyBudgetResponseDto> getOne(
            @PathVariable Long budgetId
    ) {

        MonthlyBudgetResponseDto budget =
                monthlyBudgetService.getOne(budgetId);

        return ResponseEntity.ok(budget);
    }


    // ========================================
    // 예산 수정
    // PUT /api/assets/budgets/{budgetId}
    // ========================================
    @PutMapping("/{budgetId}")
    public ResponseEntity<Void> modify(
            @PathVariable Long budgetId,
            @RequestBody MonthlyBudgetRequestDto requestDto
    ) {

        monthlyBudgetService.modify(
                budgetId,
                requestDto
        );

        return ResponseEntity.noContent().build();
    }


    // ========================================
    // 예산 삭제
    // DELETE /api/assets/budgets/{budgetId}
    // ========================================
    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long budgetId
    ) {

        monthlyBudgetService.remove(budgetId);

        return ResponseEntity.noContent().build();
    }
}