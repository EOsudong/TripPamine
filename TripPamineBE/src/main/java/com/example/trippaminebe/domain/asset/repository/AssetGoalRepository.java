package com.example.trippaminebe.domain.asset.repository;

import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetGoalRepository
        extends JpaRepository<AssetGoal, Long> {

    // 특정 사용자의 목표 전체 조회
    List<AssetGoal> findByUserId(Long userId);

    // 본인 목표만 단건 조회
    Optional<AssetGoal> findByGoalIdAndUserId(
            Long goalId,
            Long userId
    );

    // 로그인 사용자의 진행 중인 여행 목표 조회
    List<AssetGoal> findByUserIdAndGoalTypeAndStatus(
            Long userId,
            String goalType,
            String status
    );
}