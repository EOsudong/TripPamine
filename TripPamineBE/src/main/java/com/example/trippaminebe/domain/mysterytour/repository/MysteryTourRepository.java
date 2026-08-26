package com.example.trippaminebe.domain.mysterytour.repository;

import com.example.trippaminebe.domain.mysterytour.entity.MysteryTour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import java.util.List;

public interface MysteryTourRepository
        extends JpaRepository<MysteryTour, Long> {

    List<MysteryTour> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Optional<MysteryTour> findFirstByUser_IdAndStatusOrderByCreatedAtDesc(
            Long userId,
            String status
    );

    Optional<MysteryTour> findFirstByUser_IdAndStatusInOrderByCreatedAtDesc(
            Long userId,
            List<String> statuses
    );
}