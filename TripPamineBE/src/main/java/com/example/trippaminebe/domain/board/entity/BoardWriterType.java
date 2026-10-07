package com.example.trippaminebe.domain.board.entity;

// 작성자 구분. DB 컬럼이 아니라 USER_ID / ADMIN_ID 중 어느 쪽이 채워졌는지로 판단한다.
public enum BoardWriterType {
  USER,
  ADMIN
}
