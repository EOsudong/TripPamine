package com.example.trippaminebe.domain.ai.controller;

import com.example.trippaminebe.domain.ai.dto.EmotionChatRequest;
import com.example.trippaminebe.domain.ai.dto.EmotionChatResponse;
import com.example.trippaminebe.domain.ai.dto.EmotionChatMessageResponse;
import com.example.trippaminebe.domain.ai.service.EmotionChatService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/** 메인페이지 플로팅 챗봇과 감정 채팅 페이지가 공유하는 API. */
@RestController
@RequestMapping("/api/ai-manager/emotion-chat")
@RequiredArgsConstructor
public class EmotionChatController {

    private final EmotionChatService emotionChatService;

    @PostMapping("/messages")
    public EmotionChatResponse chat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody EmotionChatRequest request
    ) {
        return emotionChatService.chat(authenticatedUserId(userDetails), request);
    }

    @GetMapping("/sessions/latest")
    public Map<String, Long> latest(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return java.util.Collections.singletonMap(
                "conversationId", emotionChatService.latestOpen(authenticatedUserId(userDetails)));
    }

    @GetMapping("/sessions/{conversationId}/messages")
    public List<EmotionChatMessageResponse> messages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long conversationId
    ) {
        return emotionChatService.messages(authenticatedUserId(userDetails), conversationId);
    }

    private Long authenticatedUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null
                || userDetails.getUser().getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return userDetails.getUser().getId();
    }
}
