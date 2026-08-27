package com.example.trippaminebe.domain.mysterytour.repository;

import com.example.trippaminebe.domain.mysterytour.entity.MysteryQuest;
import com.example.trippaminebe.domain.mysterytour.entity.MysteryProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MysteryQuestRepository
        extends JpaRepository<MysteryQuest, Long> {

    List<MysteryQuest> findByMysteryTour_MysteryTourIdOrderByQuestOrderAsc(
            Long mysteryTourId
    );

    Optional<MysteryQuest>
    findByMysteryTour_MysteryTourIdAndQuestOrder(
            Long mysteryTourId,
            Integer questOrder
    );

    Optional<MysteryQuest>
    findFirstByMysteryTour_MysteryTourIdAndStatusOrderByQuestOrderAsc(
            Long mysteryTourId,
            MysteryProgressStatus status
    );

    Optional<MysteryQuest>
    findFirstByMysteryTour_MysteryTourIdOrderByQuestOrderAsc(
            Long mysteryTourId
    );

    Optional<MysteryQuest>
    findFirstByMysteryTour_MysteryTourIdAndQuestOrderGreaterThanOrderByQuestOrderAsc(
            Long mysteryTourId,
            Integer questOrder
    );

    long countByMysteryTour_MysteryTourId(Long mysteryTourId);
}
