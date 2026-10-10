package com.example.trippaminebe.domain.board.exception;

// 이미 신고한 글을 다시 신고하거나, 처리할 신고가 없는 등 현재 상태와 충돌하는 요청
public class BoardConflictException extends RuntimeException {
  public BoardConflictException(String message) {
    super(message);
  }
}
