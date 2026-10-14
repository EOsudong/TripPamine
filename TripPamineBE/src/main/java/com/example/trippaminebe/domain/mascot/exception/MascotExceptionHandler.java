package com.example.trippaminebe.domain.mascot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// quest.exception.QuestExceptionHandler와 완전히 동일한 패턴 - mascot 도메인 전용 예외만 처리
@RestControllerAdvice
public class MascotExceptionHandler {

    @ExceptionHandler(MascotSpawnNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSpawnNotFound(MascotSpawnNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MascotLocationException.class)
    public ResponseEntity<Map<String, Object>> handleLocationError(MascotLocationException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(EncounterTokenInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleTokenInvalid(EncounterTokenInvalidException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    // [추가] 연동 퀘스트 미클리어
    @ExceptionHandler(MascotLockedException.class)
    public ResponseEntity<Map<String, Object>> handleLocked(MascotLockedException e) {
        return body(HttpStatus.FORBIDDEN, e.getMessage());
    }

    // [추가] 관리자 삭제 충돌
    @ExceptionHandler(MascotDeleteConflictException.class)
    public ResponseEntity<Map<String, Object>> handleDeleteConflict(MascotDeleteConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(Map.of(
                        "status", status.value(),
                        "message", message
                ));
    }
}
