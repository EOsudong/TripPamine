package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.AssetDashboardResponseDto;
import com.example.trippaminebe.domain.asset.service.AssetDashboardService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/assets/dashboard")
@RequiredArgsConstructor
public class AssetDashboardController {

    private final AssetDashboardService assetDashboardService;


    /**
     * 로그인 사용자의 자산관리 Dashboard 조회
     *
     * 요청 예:
     *
     * GET /api/assets/dashboard?year=2026&month=10
     *
     * Authorization:
     * Bearer JWT_TOKEN
     */
    @GetMapping
    public ResponseEntity<AssetDashboardResponseDto> getDashboard(

            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @RequestParam Integer year,

            @RequestParam Integer month

    ) {

        // =========================================
        // 1. 로그인 사용자 확인
        // =========================================

        if (userDetails == null) {

            System.out.println(
                    "[AssetDashboardController] 로그인 사용자 정보 없음"
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }


        // =========================================
        // 2. 현재 로그인한 사용자의 userId 가져오기
        // =========================================

        Long userId =
                userDetails
                        .getUser()
                        .getId();


        // =========================================
        // 3. 개발 확인용 로그
        // =========================================

        System.out.println(
                "========================================="
        );

        System.out.println(
                "[AssetDashboardController]"
        );

        System.out.println(
                "로그인 사용자 ID = " + userId
        );

        System.out.println(
                "로그인 사용자 이메일 = "
                        + userDetails.getUsername()
        );

        System.out.println(
                "조회 년월 = "
                        + year
                        + "-"
                        + month
        );

        System.out.println(
                "========================================="
        );


        // =========================================
        // 4. 로그인 사용자의 Dashboard 조회
        // =========================================

        AssetDashboardResponseDto response =
                assetDashboardService.getDashboard(
                        userId,
                        year,
                        month
                );


        // =========================================
        // 5. 결과 반환
        // =========================================

        return ResponseEntity.ok(
                response
        );
    }
}