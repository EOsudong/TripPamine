package com.example.trippaminebe.domain.mysterytour.repository;

import com.example.trippaminebe.domain.mysterytour.entity.MysteryTour;
import com.example.trippaminebe.domain.mysterytour.entity.MysteryProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface MysteryTourRepository
        extends JpaRepository<MysteryTour, Long> {

    List<MysteryTour> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<MysteryTour> findFirstByUser_IdAndStatusOrderByCreatedAtDesc(
            Long userId,
            MysteryProgressStatus status
    );

    Optional<MysteryTour> findFirstByUser_IdAndStatusInOrderByCreatedAtDesc(
            Long userId,
            List<MysteryProgressStatus> statuses
    );
}
