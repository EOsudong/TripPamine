package com.example.trippaminebe.domain.ai.entity;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "AI_MANAGER_MESSAGES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiManagerMessage {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_MANAGER_MESSAGES_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_MANAGER_MESSAGES_GEN",
			sequenceName = "SEQ_AI_MANAGER_MESSAGES",
			allocationSize = 1
	)
	@Column(name = "MESSAGE_ID")
	private Long messageId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "CONVERSATION_ID",
			nullable = false
	)
	private AiManagerConversation conversation;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "ROLE",
			nullable = false,
			length = 20
	)
	private AiManagerMessageRole role;

	@Lob
	@Column(
			name = "MESSAGE_TEXT",
			nullable = false
	)
	private String messageText;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "MODEL_RUN_ID")
	private AiModelRun modelRun;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;
}