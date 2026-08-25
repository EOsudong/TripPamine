package com.example.trippaminebe.domain.diary.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class DiaryExceptionHandler {

    @ExceptionHandler(TravelDiaryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDiaryNotFound(TravelDiaryNotFoundException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of(
                "status", 404,
                "message", e.getMessage()
            ));
    }
}
