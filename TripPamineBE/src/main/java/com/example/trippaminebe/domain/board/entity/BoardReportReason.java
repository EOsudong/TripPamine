package com.example.trippaminebe.domain.board.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 게시글 신고 사유
@Getter
@RequiredArgsConstructor
public enum BoardReportReason {
  SPAM("스팸/광고"),
  ABUSE("욕설/비방"),
  OBSCENE("음란/선정성"),
  PRIVACY("개인정보 노출"),
  FALSE_INFO("허위 정보"),
  ETC("기타");

  private final String label;
}
