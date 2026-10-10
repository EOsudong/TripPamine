package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetRequestDto;
import com.example.trippaminebe.domain.asset.dto.MonthlyBudgetResponseDto;
import com.example.trippaminebe.domain.asset.service.MonthlyBudgetService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


/**
 * 월 예산 API
 *
 * [보안 수정]
 * - 모든 API 가 "로그인한 사용자 본인의" 월 예산만 다룬다.
 * - 등록 시 Body 의 userId 는 무시하고 로그인 사용자 ID 를 사용한다.
 * - 남의 budgetId 로 조회 · 수정 · 삭제하면 404 를 반환한다.
 */
@RestController
@RequestMapping("/api/assets/budgets")
@RequiredArgsConstructor
public class MonthlyBudgetController {

    private final MonthlyBudgetService monthlyBudgetService;


    // POST /api/assets/budgets
    @PostMapping
    public ResponseEntity<Long> register(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody MonthlyBudgetRequestDto requestDto
    ) {
        Long id =
                monthlyBudgetService.register(
                        loginUserId(userDetails),
                        requestDto
                );

        return ResponseEntity.ok(id);
    }


    // GET /api/assets/budgets  (내 월 예산 전체)
    @GetMapping
    public ResponseEntity<List<MonthlyBudgetResponseDto>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                monthlyBudgetService.getAll(
                        loginUserId(userDetails)
                )
        );
    }


    // GET /api/assets/budgets/{budgetId}
    @GetMapping("/{budgetId}")
    public ResponseEntity<MonthlyBudgetResponseDto> getOne(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long budgetId
    ) {
        return ResponseEntity.ok(
                monthlyBudgetService.getOne(
                        loginUserId(userDetails),
                        budgetId
                )
        );
    }


    // PUT /api/assets/budgets/{budgetId}
    @PutMapping("/{budgetId}")
    public ResponseEntity<Void> modify(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long budgetId,
            @RequestBody MonthlyBudgetRequestDto requestDto
    ) {
        monthlyBudgetService.modify(
                loginUserId(userDetails),
                budgetId,
                requestDto
        );

        return ResponseEntity.noContent().build();
    }


    // DELETE /api/assets/budgets/{budgetId}
    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long budgetId
    ) {
        monthlyBudgetService.remove(
                loginUserId(userDetails),
                budgetId
        );

        return ResponseEntity.noContent().build();
    }


    // =====================================================
    // 로그인 사용자 ID
    // =====================================================
    //
    // [보안 수정] 예전에는 userId 를 요청 Body 에서 받거나 아예 확인하지 않아서
    // 로그인한 누구나 다른 사람의 데이터를 조회 · 수정 · 삭제할 수 있었다.
    // 이제 JWT 로그인 정보에서만 userId 를 꺼낸다.
    // =====================================================

    private Long loginUserId(
            CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        return userDetails.getUser().getId();
    }

}
