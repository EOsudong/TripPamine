package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.Quest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestRepository extends JpaRepository<Quest, Long> {
}
