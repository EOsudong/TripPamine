package com.example.trippaminebe.domain.ai.entity;

import com.example.trippaminebe.domain.travel.entity.TravelPlan;
import com.example.trippaminebe.domain.user.entity.User;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "AI_MANAGER_CONVERSATIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiManagerConversation {

	@Id
	@GeneratedValue(
			strategy = GenerationType.SEQUENCE,
			generator = "SEQ_AI_MANAGER_CONVERSATIONS_GEN"
	)
	@SequenceGenerator(
			name = "SEQ_AI_MANAGER_CONVERSATIONS_GEN",
			sequenceName = "SEQ_AI_MANAGER_CONVERSATIONS",
			allocationSize = 1
	)
	@Column(name = "CONVERSATION_ID")
	private Long conversationId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
			name = "USER_ID",
			nullable = false
	)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "PLAN_ID")
	private TravelPlan travelPlan;

	@Column(
			name = "TITLE",
			length = 200
	)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(
			name = "STATUS",
			nullable = false,
			length = 20
	)
	@Builder.Default
	private AiManagerConversationStatus status =
			AiManagerConversationStatus.OPEN;

	@Column(
			name = "CREATED_AT",
			insertable = false,
			updatable = false
	)
	private LocalDateTime createdAt;

	@Column(
			name = "UPDATED_AT",
			insertable = false
	)
	private LocalDateTime updatedAt;

	@Column(name = "LAST_MESSAGE_AT")
	private LocalDateTime lastMessageAt;

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public void recordMessage() {
		LocalDateTime now = LocalDateTime.now();

		lastMessageAt = now;
		updatedAt = now;
	}

	public void close() {
		status = AiManagerConversationStatus.CLOSED;
		updatedAt = LocalDateTime.now();
	}

	public void archive() {
		status = AiManagerConversationStatus.ARCHIVED;
		updatedAt = LocalDateTime.now();
	}
}