package com.example.trippaminebe.domain.board.repository;

import com.example.trippaminebe.domain.board.entity.BoardReport;
import com.example.trippaminebe.domain.board.entity.BoardReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BoardReportRepository extends JpaRepository<BoardReport, Long> {

  boolean existsByPost_PostIdAndReporter_Id(Long postId, Long reporterId);

  long countByPost_PostIdAndStatus(Long postId, BoardReportStatus status);

  long countByStatus(BoardReportStatus status);

  List<BoardReport> findByPost_PostIdAndStatus(Long postId, BoardReportStatus status);

  Page<BoardReport> findByPost_PostIdOrderByReportIdDesc(Long postId, Pageable pageable);

  // 관리자 "신고된 게시글" 목록: 게시글 단위로 묶어서 신고 많은 순 → 최근 신고 순
  // status가 null이면 처리 상태와 무관하게 전체 신고 기준
  // 결과 행: [postId, 신고 수, 마지막 신고 시각]
  @Query(
      value = "select r.post.postId, count(r), max(r.createDate) from BoardReport r"
          + " where (:status is null or r.status = :status)"
          + " group by r.post.postId"
          + " order by count(r) desc, max(r.createDate) desc",
      countQuery = "select count(distinct r.post.postId) from BoardReport r"
          + " where (:status is null or r.status = :status)"
  )
  Page<Object[]> findReportedPostStats(@Param("status") BoardReportStatus status, Pageable pageable);

  // 게시글별 사유 분포. 결과 행: [postId, reason, 건수]
  @Query("select r.post.postId, r.reason, count(r) from BoardReport r"
      + " where r.post.postId in :postIds and (:status is null or r.status = :status)"
      + " group by r.post.postId, r.reason")
  List<Object[]> countReasonsByPost(@Param("postIds") Collection<Long> postIds,
                                    @Param("status") BoardReportStatus status);

  // 게시글별 대기(PENDING) 신고 수. 결과 행: [postId, 건수]
  @Query("select r.post.postId, count(r) from BoardReport r"
      + " where r.post.postId in :postIds and r.status = com.example.trippaminebe.domain.board.entity.BoardReportStatus.PENDING"
      + " group by r.post.postId")
  List<Object[]> countPendingByPost(@Param("postIds") Collection<Long> postIds);
}
