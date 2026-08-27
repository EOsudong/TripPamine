package com.example.trippaminebe.domain.mysterytour.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class MysteryTourExceptionHandler {

    @ExceptionHandler(MysteryTourLocationException.class)
    public ResponseEntity<Map<String, Object>> handleLocation(
            MysteryTourLocationException e
    ) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(
                        "status", 422,
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(KakaoLocalApiException.class)
    public ResponseEntity<Map<String, Object>> handleKakaoLocalApi(
            KakaoLocalApiException e
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", 503,
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(MysteryTourStateConflictException.class)
    public ResponseEntity<Map<String, Object>> handleStateConflict(
            MysteryTourStateConflictException e
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "status", 409,
                        "message", e.getMessage()
                ));
    }
}
