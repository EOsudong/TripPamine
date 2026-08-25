package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.QuestStatus;
import com.example.trippaminebe.domain.quest.entity.UserQuestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserQuestLogRepository extends JpaRepository<UserQuestLog, Long> {

    // GPS 위치 수신 시 사용자가 현재 진행 중인 미션 목록 조회 (IDX_USER_QUEST_STATUS 인덱스 활용)
    List<UserQuestLog> findByUser_IdAndStatus(Long userId, QuestStatus status);

    // 사용자의 전체 퀘스트 수행 이력 (최신순)
    List<UserQuestLog> findByUser_IdOrderByLogIdDesc(Long userId);

    // 이미 진행 중이거나 완료한 퀘스트인지 확인 (중복 시작 방지)
    Optional<UserQuestLog> findByUser_IdAndQuest_QuestId(Long userId, Long questId);

    // AI 다이어리 생성 시 특정 유저의 퀘스트 성공률 산정용
    long countByUser_IdAndStatus(Long userId, QuestStatus status);
}
