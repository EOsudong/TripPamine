package com.example.trippaminebe.domain.board.entity;

// 게시글/댓글 공통 상태.
// ACTIVE(노출) / HIDDEN(관리자 숨김) / DELETED(삭제 - 소프트 삭제, 데이터는 남김)
public enum BoardStatus {
  ACTIVE,
  HIDDEN,
  DELETED
}
