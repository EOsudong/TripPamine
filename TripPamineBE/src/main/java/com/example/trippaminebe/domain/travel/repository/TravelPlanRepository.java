package com.example.trippaminebe.domain.travel.repository;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {

    // 특정 유저의 삭제되지 않은 여행 계획 목록 조회
    List<TravelPlan> findByUser_IdAndDelYn(Long userId, String delYn);

    // 본인 소유 검증용: planId와 userId가 모두 일치하는 데이터만 조회
    Optional<TravelPlan> findByPlanIdAndUser_IdAndDelYn(Long planId, Long userId, String delYn);
}