package com.example.trippaminebe.domain.quest.repository;

import com.example.trippaminebe.domain.quest.entity.UserRpgStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRpgStatsRepository extends JpaRepository<UserRpgStats, Long> {

    Optional<UserRpgStats> findByUser_Id(Long userId);
}
