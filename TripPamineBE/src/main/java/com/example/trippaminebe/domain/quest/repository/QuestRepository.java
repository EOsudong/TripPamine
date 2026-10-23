package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.Quest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface QuestRepository extends JpaRepository<Quest, Long> {

  // 사용자 화면 노출용 - 비활성화(IS_ACTIVE=N)된 퀘스트는 제외
  List<Quest> findByIsActiveTrue();

  List<Quest> findByChapter_ChapterId(Long chapterId);

  // 관리자가 선행 관계를 바꿀 때 대상 퀘스트 행을 잠가 충돌을 줄인다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Quest q where q.questId = :questId")
  Optional<Quest> findByIdForUpdate(@Param("questId") Long questId);
}
