package com.example.trippaminebe.domain.asset.controller;

import com.example.trippaminebe.domain.asset.dto.AssetSnapshotRequestDto;
import com.example.trippaminebe.domain.asset.dto.AssetSnapshotResponseDto;
import com.example.trippaminebe.domain.asset.service.AssetSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assets/snapshots")
@RequiredArgsConstructor
public class AssetSnapshotController {

    private final AssetSnapshotService assetSnapshotService;

    // 자산 스냅샷 등록
    @PostMapping
    public ResponseEntity<Long> register(
            @RequestBody AssetSnapshotRequestDto requestDto
    ) {

        Long snapshotId = assetSnapshotService.register(requestDto);

        return ResponseEntity.ok(snapshotId);
    }


    // 전체 자산 스냅샷 조회
    @GetMapping
    public ResponseEntity<List<AssetSnapshotResponseDto>> getAll() {

        List<AssetSnapshotResponseDto> snapshots =
                assetSnapshotService.getAll();

        return ResponseEntity.ok(snapshots);
    }


    // 특정 자산 스냅샷 조회
    @GetMapping("/{snapshotId}")
    public ResponseEntity<AssetSnapshotResponseDto> getOne(
            @PathVariable Long snapshotId
    ) {

        AssetSnapshotResponseDto snapshot =
                assetSnapshotService.getOne(snapshotId);

        return ResponseEntity.ok(snapshot);
    }


    // 자산 스냅샷 삭제
    @DeleteMapping("/{snapshotId}")
    public ResponseEntity<Void> remove(
            @PathVariable Long snapshotId
    ) {

        assetSnapshotService.remove(snapshotId);

        return ResponseEntity.noContent().build();
    }
}