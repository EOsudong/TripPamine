package com.example.trippaminebe.domain.mascot.controller;

import com.example.trippaminebe.domain.mascot.dto.request.MascotCatchRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotEncounterRequest;
import com.example.trippaminebe.domain.mascot.dto.response.MascotCatchResponse;
import com.example.trippaminebe.domain.mascot.dto.response.MascotEncounterResponse;
import com.example.trippaminebe.domain.mascot.dto.response.MascotSpawnPointResponse;
import com.example.trippaminebe.domain.mascot.dto.response.UserMascotCollectionResponse;
import com.example.trippaminebe.domain.mascot.service.MascotService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

// 지역 마스코트 AR 수집(포켓몬고 스타일) 사용자 API.
// quest.controller.QuestController와 완전히 같은 구조 - JWT로 식별한 로그인 사용자 본인의
// 요청만 처리하므로, 클라이언트가 userId를 임의로 조작해서 남의 도감에 영향을 줄 수 없다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/mascots")
public class MascotController {

    private final MascotService mascotService;

    private Long getUserId(CustomUserDetails userDetails) {
        return userDetails.getUser().getId();
    }

    @GetMapping("/nearby")
    @Operation(summary = "주변 마스코트 스폰 지점 조회 (지도 표시용)")
    public ResponseEntity<List<MascotSpawnPointResponse>> findNearby(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @RequestParam(required = false) BigDecimal lat,
        @RequestParam(required = false) BigDecimal lng,
        @RequestParam(required = false, defaultValue = "10") Double radiusKm
    ) {
        return ResponseEntity.ok(mascotService.findNearby(getUserId(userDetails), lat, lng, radiusKm));
    }

    @PostMapping("/spawns/{spawnId}/encounter")
    @Operation(summary = "마스코트 조우 시도 (GPS 반경 1차 검증, 성공 시 encounterToken 발급)")
    public ResponseEntity<MascotEncounterResponse> encounter(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long spawnId,
        @Valid @RequestBody MascotEncounterRequest request
    ) {
        return ResponseEntity.ok(mascotService.encounter(getUserId(userDetails), spawnId, request));
    }

    @PostMapping("/spawns/{spawnId}/catch")
    @Operation(summary = "포획 최종 확정 (안드로이드 의사 AR 미니게임 결과 + encounterToken 재검증)")
    public ResponseEntity<MascotCatchResponse> catchMascot(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long spawnId,
        @Valid @RequestBody MascotCatchRequest request
    ) {
        return ResponseEntity.ok(mascotService.catchMascot(getUserId(userDetails), spawnId, request));
    }

    @GetMapping("/collection")
    @Operation(summary = "내 마스코트 도감 조회")
    public ResponseEntity<List<UserMascotCollectionResponse>> findMyCollection(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(mascotService.findMyCollection(getUserId(userDetails)));
    }
}
