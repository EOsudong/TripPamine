package com.example.trippaminebe.domain.nego.controller;

import com.example.trippaminebe.domain.nego.client.MyRealTripApiClient;
import com.example.trippaminebe.domain.nego.dto.request.MyRealTripTourSearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 제휴 상품 검색. 기존 /nego/** JWT 인증 정책을 그대로 사용한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/nego/partners/myrealtrip")
public class MyRealTripProductController {

    private final MyRealTripApiClient myRealTripApiClient;

    @PostMapping("/tours/search")
    @Operation(summary = "마이리얼트립 투어·티켓 검색(1차 연동 검증용)")
    public ResponseEntity<String> searchTours(
            @Valid @RequestBody MyRealTripTourSearchRequest request
    ) {
        // Spring Boot 4의 기본 Jackson 3 처리기에 Jackson 2 JsonNode를 직접 넘기지 않는다.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(myRealTripApiClient.searchTours(request.keyword().trim()).toString());
    }
}
