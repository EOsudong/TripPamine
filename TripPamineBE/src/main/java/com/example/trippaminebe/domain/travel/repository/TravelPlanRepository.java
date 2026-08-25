package com.example.trippaminebe.domain.travel.repository;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TravelPlanRepository extends JpaRepository<TravelPlan, Long> {

    // 특정 유저의 삭제되지 않은 여행 계획 목록 조회
    List<TravelPlan> findByUser_IdAndDelYn(Long userId, String delYn);

    // 본인 소유 검증용: planId와 userId가 모두 일치하는 데이터만 조회
    Optional<TravelPlan> findByPlanIdAndUser_IdAndDelYn(Long planId, Long userId, String delYn);

    // [AI 타임 네고 추가] 출발 시각이 특정 구간(예: 지금 ~ 3시간 후) 사이인, 삭제되지 않은 여행 계획 조회
    // AiNegoScheduler가 "출발 임박 여행"을 찾아 실시간 핫딜을 발송하는 데 사용
    List<TravelPlan> findByDelYnAndStartDateBetween(String delYn, LocalDateTime from, LocalDateTime to);

    // [AI 다이어리 추가] 종료 시각이 지난(=여행이 끝난), 삭제되지 않은 여행 계획 조회
    List<TravelPlan> findByUser_IdAndDelYnAndEndDateBefore(Long userId, String delYn, LocalDateTime before);
}
