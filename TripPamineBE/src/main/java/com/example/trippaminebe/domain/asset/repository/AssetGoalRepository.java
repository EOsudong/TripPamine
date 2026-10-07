package com.example.trippaminebe.domain.asset.repository;

import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetGoalRepository
        extends JpaRepository<AssetGoal, Long> {

    // 특정 사용자의 목표 전체 조회
    List<AssetGoal> findByUserId(Long userId);
}