package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.asset.dto.AssetSnapshotRequestDto;
import com.example.trippaminebe.domain.asset.dto.AssetSnapshotResponseDto;
import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
import com.example.trippaminebe.domain.asset.repository.AssetSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;


/**
 * 자산 Snapshot (수동 등록 / 조회용)
 *
 * [수정]
 * - 로그인 사용자 본인의 Snapshot 만 다룬다.
 * - 같은 날짜로 다시 등록하면 새 행을 만들지 않고 기존 행을 갱신한다
 *   (사용자당 하루 1개 — AssetSnapshotWriter 와 같은 규칙).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetSnapshotService {

    private final AssetSnapshotRepository assetSnapshotRepository;
    private final AssetSnapshotWriter assetSnapshotWriter;


    public Long register(
            Long userId,
            AssetSnapshotRequestDto dto
    ) {
        LocalDate snapshotDate =
                dto.getSnapshotDate() != null
                        ? dto.getSnapshotDate()
                        : LocalDate.now();

        assetSnapshotWriter.upsert(
                userId,
                snapshotDate,
                orZero(dto.getTotalAsset()),
                orZero(dto.getTotalIncome()),
                orZero(dto.getTotalExpense())
        );

        return assetSnapshotRepository
                .findByUserIdAndSnapshotDateOrderBySnapshotIdAsc(userId, snapshotDate)
                .stream()
                .findFirst()
                .map(AssetSnapshot::getSnapshotId)
                .orElse(null);
    }


    public List<AssetSnapshotResponseDto> getAll(
            Long userId
    ) {
        return assetSnapshotRepository
                .findByUserIdOrderBySnapshotDateAsc(userId)
                .stream()
                .map(AssetSnapshotResponseDto::new)
                .toList();
    }


    public AssetSnapshotResponseDto getOne(
            Long userId,
            Long snapshotId
    ) {
        return new AssetSnapshotResponseDto(
                findOwned(userId, snapshotId)
        );
    }


    @Transactional
    public void remove(
            Long userId,
            Long snapshotId
    ) {
        assetSnapshotRepository.delete(
                findOwned(userId, snapshotId)
        );
    }


    private AssetSnapshot findOwned(
            Long userId,
            Long snapshotId
    ) {
        return assetSnapshotRepository
                .findBySnapshotIdAndUserId(snapshotId, userId)
                .orElseThrow(() -> notFound("자산 Snapshot", snapshotId));
    }


    private long orZero(
            Long value
    ) {
        return value != null ? value : 0L;
    }


    private ResponseStatusException notFound(
            String what,
            Long id
    ) {
        // 남의 데이터인지 여부를 노출하지 않도록 "없음" 으로 통일
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                what + "을(를) 찾을 수 없습니다. id=" + id
        );
    }

}
