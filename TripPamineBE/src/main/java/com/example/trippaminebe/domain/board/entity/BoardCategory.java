package com.example.trippaminebe.domain.board.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 게시판 카테고리.
// NOTICE(공지)는 관리자만 작성할 수 있고, 나머지는 회원이 자유롭게 작성하는 커뮤니티 카테고리.
@Getter
@RequiredArgsConstructor
public enum BoardCategory {
  NOTICE("공지사항", false),
  REVIEW("여행후기", true),
  QNA("질문", true),
  FREE("자유", true);

  private final String label;
  private final boolean userWritable; // 일반 회원이 작성 가능한 카테고리인지
}
