package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.QuestPrerequisite;
import com.example.trippaminebe.domain.quest.entity.QuestPrerequisiteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuestPrerequisiteRepository extends JpaRepository<QuestPrerequisite, QuestPrerequisiteId> {

    List<QuestPrerequisite> findById_QuestIdOrderById_PrerequisiteQuestIdAsc(Long questId);

    List<QuestPrerequisite> findById_QuestIdIn(Collection<Long> questIds);

    boolean existsById_QuestId(Long questId);

    boolean existsById_PrerequisiteQuestId(Long prerequisiteQuestId);
}
