package com.example.trippaminebe.domain.asset.repository;

import com.example.trippaminebe.domain.asset.entity.AssetGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetGoalRepository
        extends JpaRepository<AssetGoal, Long> {

    // 특정 사용자의 목표 전체 조회
    List<AssetGoal> findByUserId(Long userId);


    // [보안 수정] 본인 목표만 단건 조회 (남의 goalId로 조회/수정/삭제 방지)
    Optional<AssetGoal> findByGoalIdAndUserId(Long goalId, Long userId);

}
