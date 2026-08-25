package com.example.trippaminebe.domain.accountbook.repository;

import com.example.trippaminebe.domain.accountbook.entity.TravelLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelLedgerRepository extends JpaRepository<TravelLedger, String> {

    // 특정 여행 계획의 지출 내역 전체 조회 (AI 다이어리 소비 패턴 분석용)
    List<TravelLedger> findByTravelPlan_PlanId(Long planId);
}
