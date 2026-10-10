package com.example.trippaminebe.domain.board.repository;

import com.example.trippaminebe.domain.board.entity.BoardPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// 목록 검색은 조건(카테고리/상태/검색타입/키워드)이 선택적이라 JpaSpecificationExecutor + BoardPostSpecs 사용.
// 카운트 컬럼은 동시 요청에서도 값이 꼬이지 않도록 "읽고-더해서-저장" 대신 DB UPDATE 한 번으로 증감한다.
public interface BoardPostRepository extends JpaRepository<BoardPost, Long>, JpaSpecificationExecutor<BoardPost> {

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update BoardPost p set p.viewCount = p.viewCount + 1 where p.postId = :postId")
  int increaseViewCount(@Param("postId") Long postId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update BoardPost p set p.likeCount = p.likeCount + :delta where p.postId = :postId")
  int addLikeCount(@Param("postId") Long postId, @Param("delta") long delta);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update BoardPost p set p.commentCount = p.commentCount + :delta where p.postId = :postId")
  int addCommentCount(@Param("postId") Long postId, @Param("delta") long delta);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update BoardPost p set p.reportCount = p.reportCount + 1 where p.postId = :postId")
  int increaseReportCount(@Param("postId") Long postId);

  @Query("select p.likeCount from BoardPost p where p.postId = :postId")
  Optional<Long> findLikeCount(@Param("postId") Long postId);
}



