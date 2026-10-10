package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.AssetGoalRequestDto;
import com.example.trippaminebe.domain.asset.dto.AssetGoalResponseDto;
import com.example.trippaminebe.domain.asset.service.AssetGoalService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


/**
 * 자산 목표 API
 *
 * [보안 수정]
 * - 모든 API 가 "로그인한 사용자 본인의" 자산 목표만 다룬다.
 * - 등록 시 Body 의 userId 는 무시하고 로그인 사용자 ID 를 사용한다.
 * - 남의 goalId 로 조회 · 수정 · 삭제하면 404 를 반환한다.
 */
@RestController
@RequestMapping("/api/assets/goals")
@RequiredArgsConstructor
public class AssetGoalController {

    private final AssetGoalService assetGoalService;


    // POST /api/assets/goals
    @PostMapping
    public ResponseEntity<Long> register(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AssetGoalRequestDto requestDto
    ) {
        Long id =
                assetGoalService.register(
                        loginUserId(userDetails),
                        requestDto
                );

        return ResponseEntity.ok(id);
    }


    // GET /api/assets/goals  (내 자산 목표 전체)
    @GetMapping
    public ResponseEntity<List<AssetGoalResponseDto>> getAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
                assetGoalService.getAll(
                        loginUserId(userDetails)
                )
        );
    }


    // GET /api/assets/goals/{goalId}
    @GetMapping("/{goalId}")
    public ResponseEntity<AssetGoalResponseDto> getOne(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long goalId
    ) {
        return ResponseEntity.ok(
                assetGoalService.getOne(
                        loginUserId(userDetails),
                        goalId
                )
        );
    }


    // PUT /api/assets/goals/{goalId}
    @PutMapping("/{goalId}")
    public ResponseEntity<Void> modify(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long goalId,
            @RequestBody AssetGoalRequestDto requestDto
    ) {
        assetGoalService.modify(
                loginUserId(userDetails),
                goalId,
                requestDto
        );

        return ResponseEntity.noContent().build();
    }


    // DELETE /api/assets/goals/{goalId}
    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long goalId
    ) {
        assetGoalService.remove(
                loginUserId(userDetails),
                goalId
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
