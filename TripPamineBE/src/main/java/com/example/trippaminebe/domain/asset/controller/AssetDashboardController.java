package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.AssetDashboardResponseDto;
import com.example.trippaminebe.domain.asset.dto.AssetForecastResponseDto;
import com.example.trippaminebe.domain.asset.dto.AssetHistoryResponseDto;
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


    // =====================================================
    // Dashboard 전체 조회
    // =====================================================
    //
    // GET /api/assets/dashboard?year=2026&month=10
    //
    // =====================================================

    @GetMapping
    public ResponseEntity<AssetDashboardResponseDto> getDashboard(

            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @RequestParam
            Integer year,

            @RequestParam
            Integer month

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
        // 2. 로그인 사용자 ID
        // =========================================

        Long userId =
                userDetails
                        .getUser()
                        .getId();


        // =========================================
        // 3. 로그
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
        // 4. Dashboard 조회
        // =========================================

        AssetDashboardResponseDto response =
                assetDashboardService
                        .getDashboard(
                                userId,
                                year,
                                month
                        );


        // =========================================
        // 5. 반환
        // =========================================

        return ResponseEntity.ok(
                response
        );
    }


    // =====================================================
    // 미래 자산 시뮬레이션
    // =====================================================
    //
    // 기본 요청
    //
    // GET /api/assets/dashboard/forecast
    //
    //
    // 특정 여행 목표 선택
    //
    // GET /api/assets/dashboard/forecast?goalId=6
    //
    // =====================================================

    @GetMapping("/forecast")
    public ResponseEntity<AssetForecastResponseDto> getAssetForecast(

            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @RequestParam(required = false)
            Long goalId

    ) {

        // =========================================
        // 1. 로그인 사용자 확인
        // =========================================

        if (userDetails == null) {

            System.out.println(
                    "[Asset Forecast] 로그인 사용자 정보 없음"
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }


        // =========================================
        // 2. 로그인 사용자 ID
        // =========================================

        Long userId =
                userDetails
                        .getUser()
                        .getId();


        // =========================================
        // 3. 로그
        // =========================================

        System.out.println(
                "========================================="
        );

        System.out.println(
                "[Asset Forecast Controller]"
        );

        System.out.println(
                "로그인 사용자 ID = "
                        + userId
        );

        System.out.println(
                "선택한 여행 목표 ID = "
                        + goalId
        );

        System.out.println(
                "========================================="
        );


        // =========================================
        // 4. 미래 자산 계산
        // =========================================

        AssetForecastResponseDto response =
                assetDashboardService
                        .getAssetForecast(
                                userId,
                                goalId
                        );


        // =========================================
        // 5. 반환
        // =========================================

        return ResponseEntity.ok(
                response
        );
    }


    // =====================================================
    // 기간별 자산 흐름 조회
    // =====================================================
    //
    // WEEK
    // 최근 7일
    //
    // MONTH
    // 최근 1개월
    //
    // QUARTER
    // 최근 3개월
    //
    // YEAR
    // 최근 1년
    //
    // GET /api/assets/dashboard/history?period=MONTH
    //
    // =====================================================

    @GetMapping("/history")
    public ResponseEntity<AssetHistoryResponseDto> getAssetHistory(

            @AuthenticationPrincipal
            CustomUserDetails userDetails,

            @RequestParam(defaultValue = "MONTH")
            String period

    ) {

        // =========================================
        // 1. 로그인 사용자 확인
        // =========================================

        if (userDetails == null) {

            System.out.println(
                    "[AssetDashboardController]"
                            + " 자산흐름 조회 - 로그인 사용자 정보 없음"
            );

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }


        // =========================================
        // 2. 로그인 사용자 ID
        // =========================================

        Long userId =
                userDetails
                        .getUser()
                        .getId();


        // =========================================
        // 3. 로그
        // =========================================

        System.out.println(
                "========================================="
        );

        System.out.println(
                "[Asset History Controller]"
        );

        System.out.println(
                "로그인 사용자 ID = "
                        + userId
        );

        System.out.println(
                "로그인 사용자 이메일 = "
                        + userDetails.getUsername()
        );

        System.out.println(
                "조회 기간 = "
                        + period
        );

        System.out.println(
                "========================================="
        );


        // =========================================
        // 4. 기간별 자산 흐름 조회
        // =========================================

        AssetHistoryResponseDto history =
                assetDashboardService
                        .getAssetHistoryByPeriod(
                                userId,
                                period
                        );


        // =========================================
        // 5. 반환
        // =========================================

        return ResponseEntity.ok(
                history
        );
    }
}