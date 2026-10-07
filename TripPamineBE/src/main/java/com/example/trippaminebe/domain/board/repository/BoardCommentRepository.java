package com.example.trippaminebe.domain.board.repository;

import com.example.trippaminebe.domain.board.entity.BoardComment;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

  // 사용자 화면: 노출(ACTIVE) 댓글만 오래된 순
  Page<BoardComment> findByPost_PostIdAndStatusOrderByCommentIdAsc(Long postId, BoardStatus status, Pageable pageable);

  // 관리자 화면: 상태와 무관하게 전체
  Page<BoardComment> findByPost_PostIdOrderByCommentIdAsc(Long postId, Pageable pageable);
}
