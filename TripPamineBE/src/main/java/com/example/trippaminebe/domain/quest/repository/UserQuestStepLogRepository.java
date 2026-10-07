package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.UserQuestStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface UserQuestStepLogRepository extends JpaRepository<UserQuestStepLog, Long> {

    // 이 퀘스트 로그(= 한 사용자의 한 연쇄 퀘스트 진행 기록) 안에서 이미 클리어한 단계 ID 집합.
    // "다음에 클리어해야 할 단계"는 전체 QUEST_STEPS 중 이 집합에 없는 가장 작은 STEP_ORDER로 계산한다.
    @Query("select s.step.stepId from UserQuestStepLog s where s.questLog.logId = :logId")
    Set<Long> findClearedStepIdsByLogId(Long logId);

    // 이미 이 단계를 클리어했는지 단건 확인(중복 클리어 방지용으로도 사용 가능)
    Optional<UserQuestStepLog> findByQuestLog_LogIdAndStep_StepId(Long logId, Long stepId);

    // 진행률(N/M) 표시에 사용
    long countByQuestLog_LogId(Long logId);
}
