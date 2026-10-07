package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiModelRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiModelRunRepository extends JpaRepository<AiModelRun, Long> {

	Optional<AiModelRun> findTopByUser_IdOrderByCreatedAtDesc(Long userId);
}
