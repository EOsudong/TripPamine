package com.example.trippaminebe.domain.ai.repository;

import com.example.trippaminebe.domain.ai.entity.AiManagerMessage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiManagerMessageRepository
		extends JpaRepository<AiManagerMessage, Long> {

	Page<AiManagerMessage>
	findByConversation_ConversationId(
			Long conversationId,
			Pageable pageable
	);

	long countByConversation_ConversationId(
			Long conversationId
	);

	long countByConversation_ConversationIdAndRole(
			Long conversationId,
			com.example.trippaminebe.domain.ai.entity.AiManagerMessageRole role
	);
}