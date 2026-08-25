package com.example.trippaminebe.domain.diary.repository;

import com.example.trippaminebe.domain.diary.entity.TravelDiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelDiaryRepository extends JpaRepository<TravelDiary, Long> {

    // 사용자가 작성한 AI 다이어리 목록을 최신순으로 조회 (IDX_DIARIES_USER_DATE 인덱스 활용)
    List<TravelDiary> findByUser_IdOrderByCreateDateDesc(Long userId);

    // 본인 소유 검증용
    Optional<TravelDiary> findByDiaryIdAndUser_Id(Long diaryId, Long userId);

    // 이미 해당 여행 계획에 대한 다이어리가 생성되었는지 확인 (중복 생성 방지)
    Optional<TravelDiary> findByTravelPlan_PlanId(Long planId);
}
