package com.example.trippaminebe.domain.board.exception;

// 본인 글이 아닌데 수정/삭제하려 하거나, 공지 카테고리에 회원이 글을 쓰려는 등 권한이 없는 경우
public class BoardAccessDeniedException extends RuntimeException {
  public BoardAccessDeniedException(String message) {
    super(message);
  }
}
