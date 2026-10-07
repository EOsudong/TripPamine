package com.example.trippaminebe.domain.board.dto.request;

// 목록 검색 기준. 미지정 시 TITLE_CONTENT(제목+내용)
public enum BoardSearchType {
  TITLE,
  CONTENT,
  TITLE_CONTENT,
  WRITER
}
