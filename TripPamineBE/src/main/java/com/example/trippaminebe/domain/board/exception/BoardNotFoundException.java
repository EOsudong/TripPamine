package com.example.trippaminebe.domain.board.exception;

// 게시글/댓글이 없거나, 숨김·삭제되어 사용자에게 보이면 안 되는 경우
public class BoardNotFoundException extends RuntimeException {
  public BoardNotFoundException(String message) {
    super(message);
  }
}
