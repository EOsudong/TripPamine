package com.example.trippaminebe.domain.ai.service;

import com.example.trippaminebe.domain.ai.dto.EmotionChatMessageResponse;
import com.example.trippaminebe.domain.ai.entity.AiManagerConversation;
import com.example.trippaminebe.domain.ai.entity.AiManagerConversationStatus;
import com.example.trippaminebe.domain.ai.entity.AiManagerMessage;
import com.example.trippaminebe.domain.ai.entity.AiManagerMessageRole;
import com.example.trippaminebe.domain.ai.repository.AiManagerConversationRepository;
import com.example.trippaminebe.domain.ai.repository.AiManagerMessageRepository;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** DB만 담당한다. 모델 HTTP 호출은 트랜잭션 밖에서 진행한다. */
@Service
@RequiredArgsConstructor
public class EmotionChatPersistenceService {

    private final AiManagerConversationRepository conversationRepository;
    private final AiManagerMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Long latestOpen(Long userId) {
        return conversationRepository.findTop20ByUser_IdAndStatusOrderByUpdatedAtDesc(
                userId, AiManagerConversationStatus.OPEN)
                .stream().findFirst().map(AiManagerConversation::getConversationId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<EmotionChatMessageResponse> messages(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        return messageRepository.findByConversation_ConversationId(
                conversationId,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "messageId"))
        ).getContent().reversed().stream()
                .filter(message -> message.getRole() == AiManagerMessageRole.USER
                        || message.getRole() == AiManagerMessageRole.ASSISTANT)
                .map(message -> new EmotionChatMessageResponse(
                        message.getMessageId(), message.getRole().name(), message.getMessageText(), message.getCreatedAt()
                )).toList();
    }

    /** 생성된 AI 답변이 정상일 때만 사용자/AI 두 메시지를 원자적으로 저장한다. */
    @Transactional
    public Long recordExchange(Long userId, Long conversationId, String question, String answer) {
        AiManagerConversation conversation;
        if (conversationId == null) {
            var user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
            conversation = conversationRepository.save(AiManagerConversation.builder()
                    .user(user)
                    .title(question.substring(0, Math.min(question.length(), 40)))
                    .status(AiManagerConversationStatus.OPEN)
                    .build());
        } else {
            conversation = requireOwned(userId, conversationId);
        }
        if (conversation.getStatus() != AiManagerConversationStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "종료된 대화입니다.");
        }
        messageRepository.save(AiManagerMessage.builder()
                .conversation(conversation)
                .role(AiManagerMessageRole.USER)
                .messageText(question)
                .build());
        messageRepository.save(AiManagerMessage.builder()
                .conversation(conversation)
                .role(AiManagerMessageRole.ASSISTANT)
                .messageText(answer)
                .build());
        conversation.recordMessage();
        return conversation.getConversationId();
    }

    @Transactional(readOnly = true)
    public List<AiManagerMessage> recent(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        return messageRepository.findByConversation_ConversationId(
                conversationId,
                PageRequest.of(0, 11, Sort.by(Sort.Direction.DESC, "messageId"))
        ).getContent().reversed();
    }

    @Transactional(readOnly = true)
    public long userTurnCount(Long userId, Long conversationId) {
        requireOwned(userId, conversationId);
        return messageRepository.countByConversation_ConversationIdAndRole(
                conversationId, AiManagerMessageRole.USER);
    }

    private AiManagerConversation requireOwned(Long userId, Long conversationId) {
        return conversationRepository.findByConversationIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화를 찾을 수 없습니다."));
    }
}
