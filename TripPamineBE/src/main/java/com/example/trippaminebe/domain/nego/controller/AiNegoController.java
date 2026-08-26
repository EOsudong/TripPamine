package com.example.trippaminebe.domain.nego.controller;

import com.example.trippaminebe.domain.nego.dto.response.AiNegoLogResponse;
import com.example.trippaminebe.domain.nego.dto.resquest.NegoAcceptRequest;
import com.example.trippaminebe.domain.nego.service.AiNegoService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// AI 타임 네고 및 실시간 핫딜 알림(AI_NEGO_LOGS) 사용자 API
@RestController
@RequiredArgsConstructor
@RequestMapping("/nego")
public class AiNegoController {

    private final AiNegoService aiNegoService;

    private Long getUserId(CustomUserDetails userDetails) {
        return userDetails.getUser().getId();
    }

    @GetMapping("/active")
    @Operation(summary = "현재 유효한(만료 전) 내 핫딜 제안 목록 조회")
    public ResponseEntity<List<AiNegoLogResponse>> findActiveOffers(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(aiNegoService
            .findActiveOffers(getUserId(userDetails)));
    }

    @PostMapping("/{negoId}/accept")
    @Operation(summary = "카운트다운 만료 전 핫딜 수락(결제 전환)")
    public ResponseEntity<AiNegoLogResponse> accept(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long negoId,
        @Valid @RequestBody NegoAcceptRequest request
    ) {
        return ResponseEntity.ok(aiNegoService.accept(getUserId(userDetails), negoId, request.getAccountId()));
    }
}
