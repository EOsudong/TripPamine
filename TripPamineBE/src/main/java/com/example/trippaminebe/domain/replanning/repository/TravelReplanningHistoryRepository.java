package com.example.trippaminebe.domain.replanning.repository;

import com.example.trippaminebe.domain.replanning.entity.TravelReplanningHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelReplanningHistoryRepository
        extends JpaRepository<TravelReplanningHistory, Long> {

    // 특정 여행 계획의 변경 이력 조회 (최신순)
    List<TravelReplanningHistory> findByPlanIdOrderByCreatedAtDesc(Long planId);

    // 특정 사용자의 변경 이력 조회 (최신순)
    List<TravelReplanningHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    // 특정 사용자의 특정 여행 계획 변경 이력 조회
    List<TravelReplanningHistory> findByPlanIdAndUserIdOrderByCreatedAtDesc(
            Long planId,
            Long userId
    );
}