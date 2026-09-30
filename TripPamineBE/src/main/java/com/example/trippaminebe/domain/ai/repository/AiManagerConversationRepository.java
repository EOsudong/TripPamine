package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiManagerConversation;
import com.example.trippaminebe.domain.ai.entity.AiManagerConversationStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiManagerConversationRepository
		extends JpaRepository<AiManagerConversation, Long> {

	Optional<AiManagerConversation>
	findByConversationIdAndUser_Id(
			Long conversationId,
			Long userId
	);

	List<AiManagerConversation>
	findTop20ByUser_IdOrderByUpdatedAtDesc(
			Long userId
	);

	List<AiManagerConversation>
	findTop20ByUser_IdAndStatusOrderByUpdatedAtDesc(
			Long userId,
			AiManagerConversationStatus status
	);
}