package com.example.trippaminebe.domain.board.repository;

import com.example.trippaminebe.domain.board.entity.BoardComment;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

  // 사용자 화면: 원댓글 페이지. 노출 중이거나, 원댓글은 지워졌어도 노출 중인 답글이 남아 있으면 포함
  // (지워진 원댓글은 "삭제된 댓글입니다"로 가려서 답글이 끊기지 않게 한다)
  @Query(
      value = "select c from BoardComment c"
          + " where c.post.postId = :postId and c.parent is null"
          + " and (c.status = :active or exists ("
          + "   select r.commentId from BoardComment r where r.parent = c and r.status = :active))"
          + " order by c.commentId asc",
      countQuery = "select count(c) from BoardComment c"
          + " where c.post.postId = :postId and c.parent is null"
          + " and (c.status = :active or exists ("
          + "   select r.commentId from BoardComment r where r.parent = c and r.status = :active))"
  )
  Page<BoardComment> findVisibleRoots(@Param("postId") Long postId,
                                      @Param("active") BoardStatus active,
                                      Pageable pageable);

  // 관리자 화면: 상태와 무관하게 원댓글 전체
  Page<BoardComment> findByPost_PostIdAndParentIsNullOrderByCommentIdAsc(Long postId, Pageable pageable);

  // 원댓글들에 달린 답글 (사용자: ACTIVE만)
  List<BoardComment> findByParent_CommentIdInAndStatusOrderByCommentIdAsc(Collection<Long> parentIds, BoardStatus status);

  // 원댓글들에 달린 답글 (관리자: 전체)
  List<BoardComment> findByParent_CommentIdInOrderByCommentIdAsc(Collection<Long> parentIds);
}
