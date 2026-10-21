package com.example.trippaminebe.domain.nego.controller;

import com.example.trippaminebe.domain.nego.dto.request.MyRealTripSmartDealRequest;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripSmartDealResponse;
import com.example.trippaminebe.domain.nego.service.MyRealTripSmartDealService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 사용자 본인 여행만 조회해 검증된 제휴 검색 결과를 추천한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/nego/partners/myrealtrip")
public class MyRealTripSmartDealController {

    private final MyRealTripSmartDealService myRealTripSmartDealService;

    @PostMapping("/recommendations")
    @Operation(summary = "내 여행 예산 기반 마이리얼트립 상품 추천(규칙 기반)")
    public ResponseEntity<MyRealTripSmartDealResponse> recommend(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MyRealTripSmartDealRequest request
    ) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return ResponseEntity.ok(myRealTripSmartDealService.recommend(
                userDetails.getUser().getId(), request));
    }
}
