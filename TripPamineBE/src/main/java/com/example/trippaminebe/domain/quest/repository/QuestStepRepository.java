package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.QuestStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestStepRepository extends JpaRepository<QuestStep, Long> {

    // 특정 연쇄 퀘스트의 단계를 순서대로 조회 (관리자/사용자 공용)
    List<QuestStep> findByQuest_QuestIdOrderByStepOrderAsc(Long questId);

    // 다음 단계 번호 자동 산정, 단계 수 노출(진행률 계산)에 사용
    long countByQuest_QuestId(Long questId);
}
