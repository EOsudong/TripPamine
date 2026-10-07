package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.Quest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestRepository extends JpaRepository<Quest, Long> {

  // 사용자 화면 노출용 - 비활성화(IS_ACTIVE=N)된 퀘스트는 제외
  List<Quest> findByIsActiveTrue();

  List<Quest> findByChapter_ChapterId(Long chapterId);
}
