package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiManagerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiManagerEventRepository extends JpaRepository<AiManagerEvent, Long> {

	List<AiManagerEvent> findTop50ByUser_IdOrderByCreatedAtDesc(Long userId);
}
