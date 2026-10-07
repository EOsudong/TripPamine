package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.QuestChapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestChapterRepository extends JpaRepository<QuestChapter, Long> {

    List<QuestChapter> findAllByOrderByOrderNoAscChapterIdAsc();
}
