package com.example.trippaminebe.domain.diary.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// basePackages로 다이어리 컨트롤러에만 적용한다.
// (범위를 안 좁히면 아래 검증 실패 핸들러가 다른 도메인의 @Valid 응답 형식까지 바꿔 버린다)
@RestControllerAdvice(basePackages = "com.example.trippaminebe.domain.diary")
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

    @ExceptionHandler(DiaryAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleDiaryAlreadyExists(DiaryAlreadyExistsException e) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(Map.of(
                "status", 409,
                "message", e.getMessage(),
                "diaryId", e.getDiaryId()
            ));
    }

    // @Valid 검증 실패 시 첫 번째 필드의 안내 문구를 그대로 내려준다 (예: "제목을 입력해 주세요.")
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("입력값을 확인해 주세요.");
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "status", 400,
                "message", message
            ));
    }
}