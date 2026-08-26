package com.example.trippaminebe.domain.finlife.exception;

import com.example.trippaminebe.domain.finlife.client.FinlifeApiException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * FINLIFE API 호출 실패 시(인증키 오류, 정부 API 장애, 타임아웃 등) 500 대신
 * 502(Bad Gateway)로 응답해서 "우리 서버 문제"가 아니라 "외부 API 연동 문제"임을 구분해준다.
 */
@Order(0)
@RestControllerAdvice
public class FinlifeApiExceptionHandler {

  @ExceptionHandler(FinlifeApiException.class)
  public ResponseEntity<Map<String, Object>> handleFinlifeApiException(FinlifeApiException e) {
    return ResponseEntity
        .status(HttpStatus.BAD_GATEWAY)
        .body(Map.of(
            "status", 502,
            "message", "금융상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
            "detail", e.getMessage()
        ));
  }
}
