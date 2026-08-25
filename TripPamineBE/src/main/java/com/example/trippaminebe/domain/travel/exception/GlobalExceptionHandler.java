package com.example.trippaminebe.domain.travel.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TravelPlanNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTravelPlanNotFound(
        TravelPlanNotFoundException e
    ) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of(
                "status", 404,
                "message", e.getMessage()
            ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
        IllegalArgumentException e
    ) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "status", 400,
                "message", e.getMessage()
            ));
    }

    // [Mock 은행 연동 추가] MockOpenBankingClient가 Mock 은행 서버와 통신 중 문제가 생기면
    // (타임아웃, 연결 실패 등) IllegalStateException으로 감싸서 던지는데, 이 핸들러가 없으면
    // 프론트가 500 에러 페이지 형태의 응답을 받게 되어 원인 메시지를 사용자에게 보여줄 수 없다.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
        IllegalStateException e
    ) {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "status", 503,
                "message", e.getMessage()
            ));
    }
}
