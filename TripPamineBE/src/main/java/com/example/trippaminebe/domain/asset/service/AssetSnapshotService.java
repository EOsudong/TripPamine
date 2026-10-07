package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.asset.dto.AssetSnapshotRequestDto;
import com.example.trippaminebe.domain.asset.dto.AssetSnapshotResponseDto;
import com.example.trippaminebe.domain.asset.entity.AssetSnapshot;
import com.example.trippaminebe.domain.asset.repository.AssetSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetSnapshotService {

    private final AssetSnapshotRepository assetSnapshotRepository;


    // 등록
    @Transactional
    public Long register(AssetSnapshotRequestDto dto) {

        AssetSnapshot assetSnapshot = AssetSnapshot.builder()
                .userId(dto.getUserId())
                .snapshotDate(dto.getSnapshotDate())
                .totalAsset(dto.getTotalAsset())
                .totalIncome(dto.getTotalIncome())
                .totalExpense(dto.getTotalExpense())
                .travelExpense(dto.getTravelExpense())
                .build();

        AssetSnapshot saved =
                assetSnapshotRepository.save(assetSnapshot);

        return saved.getSnapshotId();
    }


    // 전체 조회
    public List<AssetSnapshotResponseDto> getAll() {

        return assetSnapshotRepository
                .findAll()
                .stream()
                .map(AssetSnapshotResponseDto::new)
                .toList();
    }


    // 단건 조회
    public AssetSnapshotResponseDto getOne(Long snapshotId) {

        AssetSnapshot assetSnapshot =
                assetSnapshotRepository
                        .findById(snapshotId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "자산 스냅샷이 존재하지 않습니다. id="
                                                + snapshotId
                                )
                        );

        return new AssetSnapshotResponseDto(assetSnapshot);
    }


    // 삭제
    @Transactional
    public void remove(Long snapshotId) {

        AssetSnapshot assetSnapshot =
                assetSnapshotRepository
                        .findById(snapshotId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "자산 스냅샷이 존재하지 않습니다. id="
                                                + snapshotId
                                )
                        );

        assetSnapshotRepository.delete(assetSnapshot);
    }
}