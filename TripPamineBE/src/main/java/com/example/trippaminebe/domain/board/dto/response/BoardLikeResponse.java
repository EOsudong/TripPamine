package com.example.trippaminebe.domain.board.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 좋아요 토글 결과 - 프론트가 하트 상태와 숫자를 바로 갱신할 수 있게 둘 다 내려준다
@Getter
@AllArgsConstructor
public class BoardLikeResponse {

  private boolean liked;
  private Long likeCount;
}
