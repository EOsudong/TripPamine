package com.example.trippaminebe.domain.board.service;

import com.example.trippaminebe.domain.board.dto.response.BoardCommentResponse;
import com.example.trippaminebe.domain.board.entity.BoardComment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// 원댓글 목록 + 답글 목록을 "원댓글 아래 답글" 구조의 응답으로 조립한다 (회원/관리자 서비스 공용)
final class BoardCommentTree {

  private BoardCommentTree() {
  }

  static List<BoardCommentResponse> assemble(
      List<BoardComment> roots,
      List<BoardComment> replies,
      Function<BoardComment, BoardCommentResponse> mapper
  ) {
    Map<Long, BoardCommentResponse> byId = new LinkedHashMap<>();
    for (BoardComment root : roots) {
      byId.put(root.getCommentId(), mapper.apply(root));
    }
    for (BoardComment reply : replies) {
      BoardCommentResponse parent = byId.get(reply.getParent().getCommentId());
      if (parent != null) {
        parent.getReplies().add(mapper.apply(reply));
      }
    }
    return List.copyOf(byId.values());
  }
}
