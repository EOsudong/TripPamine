package com.example.trippaminebe.domain.quest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// travel.exception.GlobalExceptionHandler 와 동일한 패턴 - 퀘스트 도메인 전용 예외만 별도 처리
@RestControllerAdvice
public class QuestExceptionHandler {

    @ExceptionHandler(QuestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleQuestNotFound(QuestNotFoundException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of(
                "status", 404,
                "message", e.getMessage()
            ));
    }

    @ExceptionHandler(QuestDeleteConflictException.class)
    public ResponseEntity<Map<String, Object>> handleQuestDeleteConflict(QuestDeleteConflictException e) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of(
                "status", 409,
                "message", e.getMessage()
            ));
    }
}
