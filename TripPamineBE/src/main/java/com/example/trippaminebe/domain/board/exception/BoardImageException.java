package com.example.trippaminebe.domain.board.exception;

// 이미지 개수 초과, 허용되지 않는 형식, 저장 실패 등 첨부 이미지 관련 오류
public class BoardImageException extends RuntimeException {
  public BoardImageException(String message) {
    super(message);
  }

  public BoardImageException(String message, Throwable cause) {
    super(message, cause);
  }
}
