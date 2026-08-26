package com.example.trippaminebe.domain.travel.controller;

import com.example.trippaminebe.domain.travel.dto.TravelPlanRequest;
import com.example.trippaminebe.domain.travel.dto.TravelPlanResponse;
import com.example.trippaminebe.domain.travel.service.TravelPlanService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/travel-plans")
public class TravelPlanController {

    private final TravelPlanService travelPlanService;

    // 로그인한 사용자의 ID를 꺼낸다. 로그인 정보가 없으면 401.
    //
    // [보안 수정] 예전에는 이 메서드가 getUserIdSafely()라는 이름으로,
    // 로그인 정보가 없을 때 -1L(테스트 계정 test1@trippamine.com의 ID)을 대신 반환했다.
    // SecurityConfig에서 "/travel-plans/**"가 permitAll로 열려 있었기 때문에,
    // 결과적으로 토큰 없이 아무나 여행 계획을 등록/조회/수정/삭제할 수 있었고
    // 그 데이터가 전부 테스트 계정 앞으로 쌓였다. 게다가 401이 떠야 할 상황에서
    // 200이 내려오니 프론트에서 인증 문제를 알아챌 방법도 없었다.
    // 이제 SecurityConfig에서 이 경로를 인증 필수로 바꿨고, 여기서도 폴백을 제거해
    // 혹시 인증이 비어 들어오면 명시적으로 401을 던진다.
    private Long requireUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return userDetails.getUser().getId();
    }

    @PostMapping
    @Operation(summary = "여행 계획 등록")
    public ResponseEntity<TravelPlanResponse> create(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody TravelPlanRequest request
    ) {
        TravelPlanResponse response = travelPlanService.create(requireUserId(userDetails), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "내 여행 계획 목록 조회")
    public ResponseEntity<List<TravelPlanResponse>> findAll(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(
            travelPlanService.findAllByUserId(requireUserId(userDetails))
        );
    }

    @GetMapping("/{planId}")
    @Operation(summary = "여행 계획 단건 조회")
    public ResponseEntity<TravelPlanResponse> findById(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long planId
    ) {
        return ResponseEntity.ok(
            travelPlanService.findById(planId, requireUserId(userDetails))
        );
    }

    @PutMapping("/{planId}")
    @Operation(summary = "여행 계획 수정")
    public ResponseEntity<TravelPlanResponse> update(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long planId,
        @Valid @RequestBody TravelPlanRequest request
    ) {
        return ResponseEntity.ok(
            travelPlanService.update(planId, requireUserId(userDetails), request)
        );
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "여행 계획 삭제")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long planId
    ) {
        travelPlanService.delete(planId, requireUserId(userDetails));
        return ResponseEntity.noContent().build();
    }
}
