package com.example.trippaminebe.domain.mascot.controller;

import com.example.trippaminebe.domain.mascot.dto.request.MascotRequest;
import com.example.trippaminebe.domain.mascot.dto.request.MascotSpawnPointRequest;
import com.example.trippaminebe.domain.mascot.dto.request.RegionRequest;
import com.example.trippaminebe.domain.mascot.dto.response.MascotResponse;
import com.example.trippaminebe.domain.mascot.dto.response.MascotSpawnPointResponse;
import com.example.trippaminebe.domain.mascot.dto.response.RegionResponse;
import com.example.trippaminebe.domain.mascot.service.MascotService;
import com.example.trippaminebe.security.jwt.aspect.AdminLoggable;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// quest.controller.AdminQuestController와 같은 패턴 - 지역/마스코트/스폰 지점을 관리자가 등록/수정한다.
// 실제 콘텐츠(마스코트 이름/이미지, 스폰 좌표)는 여기 API로 넣거나, db/mascot_schema.sql의
// 샘플 INSERT 문을 참고해서 초기 데이터를 만들어도 된다.
@RestController
@RequestMapping("/admin/mascots")
@RequiredArgsConstructor
public class AdminMascotController {

    private final MascotService mascotService;

    // ----- 지역(Region) -----

    @GetMapping("/regions")
    @Operation(summary = "지역 목록 조회 (관리자용)")
    public ResponseEntity<List<RegionResponse>> getRegionList() {
        return ResponseEntity.ok(mascotService.findAllRegions());
    }

    @PostMapping("/regions")
    @Operation(summary = "지역 등록 (관리자용)")
    @AdminLoggable(actionType = "MASCOT_REGION_CREATE", targetTable = "REGIONS")
    public ResponseEntity<RegionResponse> createRegion(@Valid @RequestBody RegionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mascotService.createRegion(request));
    }

    @PutMapping("/regions/{regionId}")
    @Operation(summary = "지역 수정 (관리자용)")
    @AdminLoggable(actionType = "MASCOT_REGION_UPDATE", targetTable = "REGIONS")
    public ResponseEntity<RegionResponse> updateRegion(
        @PathVariable Long regionId,
        @Valid @RequestBody RegionRequest request
    ) {
        return ResponseEntity.ok(mascotService.updateRegion(regionId, request));
    }

    // ----- 마스코트(Mascot) -----

    @GetMapping
    @Operation(summary = "마스코트 목록 조회 (관리자용)")
    public ResponseEntity<List<MascotResponse>> getMascotList() {
        return ResponseEntity.ok(mascotService.findAllMascots());
    }

    @PostMapping
    @Operation(summary = "마스코트 등록 (관리자용)")
    @AdminLoggable(actionType = "MASCOT_CREATE", targetTable = "MASCOTS")
    public ResponseEntity<MascotResponse> createMascot(@Valid @RequestBody MascotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mascotService.createMascot(request));
    }

    @PutMapping("/{mascotId}")
    @Operation(summary = "마스코트 수정 (관리자용)")
    @AdminLoggable(actionType = "MASCOT_UPDATE", targetTable = "MASCOTS")
    public ResponseEntity<MascotResponse> updateMascot(
        @PathVariable Long mascotId,
        @Valid @RequestBody MascotRequest request
    ) {
        return ResponseEntity.ok(mascotService.updateMascot(mascotId, request));
    }

    // ----- 스폰 지점(MascotSpawnPoint) -----

    @GetMapping("/spawns")
    @Operation(summary = "스폰 지점 목록 조회 (관리자용)")
    public ResponseEntity<List<MascotSpawnPointResponse>> getSpawnPointList() {
        return ResponseEntity.ok(mascotService.findAllSpawnPointsForAdmin());
    }

    @PostMapping("/spawns")
    @Operation(summary = "스폰 지점 등록 (관리자용)")
    @AdminLoggable(actionType = "MASCOT_SPAWN_CREATE", targetTable = "MASCOT_SPAWN_POINTS")
    public ResponseEntity<MascotSpawnPointResponse> createSpawnPoint(
        @Valid @RequestBody MascotSpawnPointRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mascotService.createSpawnPoint(request));
    }

    @PutMapping("/spawns/{spawnId}")
    @Operation(summary = "스폰 지점 수정 (관리자용)")
    @AdminLoggable(actionType = "MASCOT_SPAWN_UPDATE", targetTable = "MASCOT_SPAWN_POINTS")
    public ResponseEntity<MascotSpawnPointResponse> updateSpawnPoint(
        @PathVariable Long spawnId,
        @Valid @RequestBody MascotSpawnPointRequest request
    ) {
        return ResponseEntity.ok(mascotService.updateSpawnPoint(spawnId, request));
    }

    @DeleteMapping("/spawns/{spawnId}")
    @Operation(summary = "스폰 지점 삭제 (관리자용)")
    @AdminLoggable(actionType = "MASCOT_SPAWN_DELETE", targetTable = "MASCOT_SPAWN_POINTS")
    public ResponseEntity<Void> deleteSpawnPoint(@PathVariable Long spawnId) {
        mascotService.deleteSpawnPoint(spawnId);
        return ResponseEntity.noContent().build();
    }
}
