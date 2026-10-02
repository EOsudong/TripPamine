package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiUserFact;
import com.example.trippaminebe.domain.ai.entity.AiUserFactStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiUserFactRepository
		extends JpaRepository<AiUserFact, Long> {

	Optional<AiUserFact>
	findByUser_IdAndFactKey(
			Long userId,
			String factKey
	);

	List<AiUserFact>
	findAllByUser_IdAndStatusOrderByUpdatedAtDesc(
			Long userId,
			AiUserFactStatus status
	);
}