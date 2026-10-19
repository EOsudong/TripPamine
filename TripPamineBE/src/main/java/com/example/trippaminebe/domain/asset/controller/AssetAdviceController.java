package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.AssetAdviceResponseDto;
import com.example.trippaminebe.domain.asset.service.AssetAdviceService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.example.trippaminebe.domain.asset.dto.TravelGoalRequestDto;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assets/advice")
public class AssetAdviceController {

    private final AssetAdviceService assetAdviceService;


    // =========================================================
    // 로그인 사용자의 여행자금 자산관리 가이드
    //
    // GET /api/assets/advice
    // =========================================================
    @GetMapping
    public ResponseEntity<AssetAdviceResponseDto> getAssetAdvice(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {

        // =====================================================
        // 1. 인증 확인
        // =====================================================
        if (userDetails == null) {

            log.warn(
                    "[AssetAdvice] 인증되지 않은 사용자의 접근"
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }


        // =====================================================
        // 2. JWT 인증 사용자 ID
        // =====================================================
        Long userId =
                userDetails
                        .getUser()
                        .getId();


        log.info(
                "[AssetAdvice] 여행자금 자산관리 가이드 조회 userId={}",
                userId
        );


        // =====================================================
        // 3. Service 호출
        // =====================================================
        AssetAdviceResponseDto response =
                assetAdviceService
                        .getAssetAdvice(userId);


        // =====================================================
        // 4. 응답
        // =====================================================
        return ResponseEntity.ok(response);
    }
    @PostMapping("/travel-goal")
    public ResponseEntity<Long> createTravelGoal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TravelGoalRequestDto request
    ) {

        // =========================================
        // 1. 로그인 여부 확인
        // =========================================
        if (userDetails == null) {

            log.warn(
                    "[AssetAdvice] 인증되지 않은 사용자의 여행 목표 등록 요청"
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }


        // =========================================
        // 2. JWT 로그인 사용자 ID 가져오기
        // =========================================
        Long userId =
                userDetails.getUser().getId();


        log.info(
                "[AssetAdvice] 여행 목표 등록 userId={}, goalName={}",
                userId,
                request.getGoalName()
        );


        // =========================================
        // 3. 여행 목표 저장
        // =========================================
        Long goalId =
                assetAdviceService.createTravelGoal(
                        userId,
                        request
                );


        // =========================================
        // 4. 생성된 GOAL_ID 반환
        // =========================================
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(goalId);
    }
// =====================================================
// 여행 목표 삭제
// =====================================================
//
// DELETE
// /api/assets/advice/travel-goal/6
//
// =====================================================

    @DeleteMapping("/travel-goal/{goalId}")
    public ResponseEntity<Void> deleteTravelGoal(

            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @PathVariable
            Long goalId

    ) {

        // 로그인 확인
        if (userDetails == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }


        Long userId =
                userDetails
                        .getUser()
                        .getId();


        assetAdviceService
                .deleteTravelGoal(
                        userId,
                        goalId
                );


        return ResponseEntity
                .noContent()
                .build();
    }
// =====================================================
// 여행 목표 수정
// =====================================================
//
// PUT /api/assets/advice/travel-goal/{goalId}
//
// =====================================================

    @PutMapping("/travel-goal/{goalId}")
    public ResponseEntity<Void> updateTravelGoal(

            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @PathVariable
            Long goalId,

            @Valid
            @RequestBody
            TravelGoalRequestDto request

    ) {

        if (userDetails == null) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }


        Long userId =
                userDetails
                        .getUser()
                        .getId();


        assetAdviceService
                .updateTravelGoal(
                        userId,
                        goalId,
                        request
                );


        return ResponseEntity
                .noContent()
                .build();
    }
    

}
