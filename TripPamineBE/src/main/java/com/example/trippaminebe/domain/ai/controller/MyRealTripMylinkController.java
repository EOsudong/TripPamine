package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.nego.dto.request.MyRealTripMylinkRequest;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripMylinkResponse;
import com.example.trippaminebe.domain.nego.service.MyRealTripMylinkService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 사용자에게 공식 제휴 추적 링크를 발급한다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/nego/partners/myrealtrip")
public class MyRealTripMylinkController {

    private final MyRealTripMylinkService myRealTripMylinkService;

    @PostMapping("/mylink")
    @Operation(summary = "마이리얼트립 상품 MyLink 추적 링크 생성")
    public ResponseEntity<MyRealTripMylinkResponse> create(
            @Valid @RequestBody MyRealTripMylinkRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(myRealTripMylinkService.create(request));
    }
}
