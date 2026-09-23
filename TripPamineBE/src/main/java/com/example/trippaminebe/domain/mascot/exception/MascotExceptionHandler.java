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
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "status", 404,
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(MascotLocationException.class)
    public ResponseEntity<Map<String, Object>> handleLocationError(MascotLocationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "status", 400,
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(EncounterTokenInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleTokenInvalid(EncounterTokenInvalidException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "status", 409,
                        "message", e.getMessage()
                ));
    }
}
