package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.asset.dto.AssetGoalRequestDto;
import com.example.trippaminebe.domain.asset.dto.AssetGoalResponseDto;
import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.repository.AssetGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetGoalService {

    private final AssetGoalRepository assetGoalRepository;


    // ========================================
    // 목표 등록
    // ========================================
    @Transactional
    public Long register(AssetGoalRequestDto dto) {

        AssetGoal assetGoal = AssetGoal.builder()
                .userId(dto.getUserId())
                .goalName(dto.getGoalName())
                .targetAmount(dto.getTargetAmount())
                .currentAmount(dto.getCurrentAmount())
                .targetDate(dto.getTargetDate())
                .goalType(dto.getGoalType())
                .status(dto.getStatus())
                .build();

        AssetGoal savedGoal =
                assetGoalRepository.save(assetGoal);

        return savedGoal.getGoalId();
    }


    // ========================================
    // 전체 목표 조회
    // ========================================
    public List<AssetGoalResponseDto> getAll() {

        return assetGoalRepository
                .findAll()
                .stream()
                .map(AssetGoalResponseDto::new)
                .toList();
    }


    // ========================================
    // 특정 목표 조회
    // ========================================
    public AssetGoalResponseDto getOne(Long goalId) {

        AssetGoal assetGoal =
                assetGoalRepository
                        .findById(goalId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "자산 목표가 존재하지 않습니다. id="
                                                + goalId
                                )
                        );

        return new AssetGoalResponseDto(assetGoal);
    }


    // ========================================
    // 목표 수정
    // ========================================
    @Transactional
    public void modify(
            Long goalId,
            AssetGoalRequestDto dto
    ) {

        AssetGoal assetGoal =
                assetGoalRepository
                        .findById(goalId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "자산 목표가 존재하지 않습니다. id="
                                                + goalId
                                )
                        );

        assetGoal.setGoalName(
                dto.getGoalName()
        );

        assetGoal.setTargetAmount(
                dto.getTargetAmount()
        );

        assetGoal.setCurrentAmount(
                dto.getCurrentAmount()
        );

        assetGoal.setTargetDate(
                dto.getTargetDate()
        );

        assetGoal.setGoalType(
                dto.getGoalType()
        );

        assetGoal.setStatus(
                dto.getStatus()
        );
    }


    // ========================================
    // 목표 삭제
    // ========================================
    @Transactional
    public void remove(Long goalId) {

        AssetGoal assetGoal =
                assetGoalRepository
                        .findById(goalId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "자산 목표가 존재하지 않습니다. id="
                                                + goalId
                                )
                        );

        assetGoalRepository.delete(assetGoal);
    }
}