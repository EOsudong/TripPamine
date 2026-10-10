package com.example.trippaminebe.domain.asset.service;

import com.example.trippaminebe.domain.asset.dto.AssetGoalRequestDto;
import com.example.trippaminebe.domain.asset.dto.AssetGoalResponseDto;
import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import com.example.trippaminebe.domain.asset.repository.AssetGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;


/**
 * 자산 목표
 *
 * [보안 수정] 모든 메서드가 로그인 사용자 ID(userId)를 받아서
 * 본인 목표만 조회 · 수정 · 삭제한다. (예전: findAll / findById 로 전체 사용자 접근 가능)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetGoalService {

    private final AssetGoalRepository assetGoalRepository;


    @Transactional
    public Long register(
            Long userId,
            AssetGoalRequestDto dto
    ) {
        AssetGoal assetGoal =
                AssetGoal.builder()
                        // Body 의 userId 는 무시
                        .userId(userId)
                        .goalName(dto.getGoalName())
                        .targetAmount(dto.getTargetAmount())
                        .currentAmount(dto.getCurrentAmount())
                        .targetDate(dto.getTargetDate())
                        .goalType(dto.getGoalType())
                        .status(dto.getStatus())
                        .build();

        return assetGoalRepository.save(assetGoal).getGoalId();
    }


    public List<AssetGoalResponseDto> getAll(
            Long userId
    ) {
        return assetGoalRepository
                .findByUserId(userId)
                .stream()
                .map(AssetGoalResponseDto::new)
                .toList();
    }


    public AssetGoalResponseDto getOne(
            Long userId,
            Long goalId
    ) {
        return new AssetGoalResponseDto(
                findOwned(userId, goalId)
        );
    }


    @Transactional
    public void modify(
            Long userId,
            Long goalId,
            AssetGoalRequestDto dto
    ) {
        AssetGoal assetGoal =
                findOwned(userId, goalId);

        assetGoal.setGoalName(dto.getGoalName());
        assetGoal.setTargetAmount(dto.getTargetAmount());
        assetGoal.setCurrentAmount(dto.getCurrentAmount());
        assetGoal.setTargetDate(dto.getTargetDate());
        assetGoal.setGoalType(dto.getGoalType());
        assetGoal.setStatus(dto.getStatus());
    }


    @Transactional
    public void remove(
            Long userId,
            Long goalId
    ) {
        assetGoalRepository.delete(
                findOwned(userId, goalId)
        );
    }


    private AssetGoal findOwned(
            Long userId,
            Long goalId
    ) {
        return assetGoalRepository
                .findByGoalIdAndUserId(goalId, userId)
                .orElseThrow(() -> notFound("자산 목표", goalId));
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
