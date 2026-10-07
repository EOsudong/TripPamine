package com.example.trippaminebe.domain.board.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

// quest/diary 도메인과 동일한 패턴 - { status, message } 형태로 응답.
// basePackages로 범위를 게시판 컨트롤러로 한정해서, 다른 도메인의 예외 처리 방식에 영향을 주지 않는다.
// (multipart 전체 용량 초과는 컨트롤러 도달 전에 Spring이 413으로 응답하므로 여기서 잡히지 않음 - 프론트에서 413 처리)
@RestControllerAdvice(basePackages = "com.example.trippaminebe.domain.board")
public class BoardExceptionHandler {

  @ExceptionHandler(BoardNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(BoardNotFoundException e) {
    return body(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(BoardAccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(BoardAccessDeniedException e) {
    return body(HttpStatus.FORBIDDEN, e.getMessage());
  }

  @ExceptionHandler(BoardImageException.class)
  public ResponseEntity<Map<String, Object>> handleImage(BoardImageException e) {
    return body(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  // @Valid 검증 실패 시 첫 번째 필드 메시지를 그대로 내려준다 (예: "제목을 입력해주세요.")
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(error -> error.getDefaultMessage())
        .orElse("입력값을 확인해주세요.");
    return body(HttpStatus.BAD_REQUEST, message);
  }

  // 좋아요 동시 클릭 등으로 유니크 제약(UK_BOARD_POST_LIKES)에 걸린 경우
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException e) {
    return body(HttpStatus.CONFLICT, "요청이 중복되었습니다. 잠시 후 다시 시도해주세요.");
  }

  private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of(
        "status", status.value(),
        "message", message
    ));
  }
}
