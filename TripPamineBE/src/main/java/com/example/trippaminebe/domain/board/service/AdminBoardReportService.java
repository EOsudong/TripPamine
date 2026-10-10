package com.example.trippaminebe.domain.board.service;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.admin.repository.AdminRepository;
import com.example.trippaminebe.domain.board.dto.request.BoardReportProcessRequest;
import com.example.trippaminebe.domain.board.dto.response.AdminBoardReportResponse;
import com.example.trippaminebe.domain.board.dto.response.AdminReportedPostResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPageResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPostSummaryResponse;
import com.example.trippaminebe.domain.board.entity.BoardPost;
import com.example.trippaminebe.domain.board.entity.BoardReport;
import com.example.trippaminebe.domain.board.entity.BoardReportReason;
import com.example.trippaminebe.domain.board.entity.BoardReportStatus;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import com.example.trippaminebe.domain.board.exception.BoardAccessDeniedException;
import com.example.trippaminebe.domain.board.exception.BoardConflictException;
import com.example.trippaminebe.domain.board.exception.BoardNotFoundException;
import com.example.trippaminebe.domain.board.repository.BoardPostRepository;
import com.example.trippaminebe.domain.board.repository.BoardReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 관리자 신고 관리: 신고된 게시글 목록(게시글 단위 집계), 게시글별 신고 내역, 일괄 처리
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoardReportService {

  private final BoardReportRepository reportRepository;
  private final BoardPostRepository postRepository;
  private final AdminRepository adminRepository;

  // status가 null이면 처리 상태와 무관하게 전체 신고 기준으로 집계
  public BoardPageResponse<AdminReportedPostResponse> getReportedPosts(BoardReportStatus status, int page, int size) {
    Pageable pageable = PageRequest.of(Math.max(page, 0), BoardService.clampSize(size, 20));
    Page<Object[]> stats = reportRepository.findReportedPostStats(status, pageable);

    List<Long> postIds = stats.getContent().stream().map(row -> (Long) row[0]).toList();
    if (postIds.isEmpty()) {
      return BoardPageResponse.from(new PageImpl<>(List.<AdminReportedPostResponse>of(), pageable, stats.getTotalElements()));
    }

    Map<Long, BoardPost> posts = postRepository.findAllById(postIds).stream()
        .collect(Collectors.toMap(BoardPost::getPostId, Function.identity()));

    Map<Long, Long> pendingByPost = new HashMap<>();
    for (Object[] row : reportRepository.countPendingByPost(postIds)) {
      pendingByPost.put((Long) row[0], (Long) row[1]);
    }

    Map<Long, Map<BoardReportReason, Long>> reasonsByPost = new HashMap<>();
    for (Object[] row : reportRepository.countReasonsByPost(postIds, status)) {
      reasonsByPost
          .computeIfAbsent((Long) row[0], id -> new EnumMap<>(BoardReportReason.class))
          .put((BoardReportReason) row[1], (Long) row[2]);
    }

    List<AdminReportedPostResponse> content = stats.getContent().stream()
        .filter(row -> posts.containsKey((Long) row[0]))
        .map(row -> {
          Long postId = (Long) row[0];
          return new AdminReportedPostResponse(
              BoardPostSummaryResponse.fromForAdmin(posts.get(postId)),
              (Long) row[1],
              pendingByPost.getOrDefault(postId, 0L),
              (LocalDateTime) row[2],
              reasonsByPost.getOrDefault(postId, Map.of())
          );
        })
        .toList();

    return BoardPageResponse.from(new PageImpl<>(content, pageable, stats.getTotalElements()));
  }

  // 게시글 하나에 들어온 신고 내역 (최신순)
  public BoardPageResponse<AdminBoardReportResponse> getPostReports(Long postId, int page, int size) {
    findPost(postId);
    Pageable pageable = PageRequest.of(Math.max(page, 0), BoardService.clampSize(size, 50));
    return BoardPageResponse.from(reportRepository.findByPost_PostIdOrderByReportIdDesc(postId, pageable)
        .map(AdminBoardReportResponse::from));
  }

  // 관리자 메뉴 배지 등에 쓰는 처리 대기 신고 수
  public long countPending() {
    return reportRepository.countByStatus(BoardReportStatus.PENDING);
  }

  // 게시글에 쌓인 대기 신고를 한 번에 처리하고, 필요하면 게시글도 숨김/삭제/복구한다.
  // 반환값: 처리한 신고 수
  @Transactional
  public int processPostReports(Long adminId, Long postId, BoardReportProcessRequest request) {
    if (request.getResult() == BoardReportStatus.PENDING) {
      throw new IllegalArgumentException("처리 결과는 ACCEPTED 또는 REJECTED만 가능합니다.");
    }
    BoardPost post = findPost(postId);
    Admin admin = adminRepository.findById(adminId)
        .orElseThrow(() -> new BoardAccessDeniedException("존재하지 않는 관리자입니다."));

    List<BoardReport> pending = reportRepository.findByPost_PostIdAndStatus(postId, BoardReportStatus.PENDING);
    if (pending.isEmpty()) {
      throw new BoardConflictException("처리할 대기 신고가 없습니다.");
    }

    String memo = StringUtils.hasText(request.getMemo()) ? request.getMemo().trim() : null;
    pending.forEach(report -> report.handle(request.getResult(), admin, memo));

    BoardReportProcessRequest.PostAction action =
        request.getPostAction() != null ? request.getPostAction() : BoardReportProcessRequest.PostAction.KEEP;
    switch (action) {
      case HIDE -> post.changeStatus(BoardStatus.HIDDEN);
      case DELETE -> post.changeStatus(BoardStatus.DELETED);
      case ACTIVATE -> post.changeStatus(BoardStatus.ACTIVE);
      case KEEP -> { }
    }
    return pending.size();
  }

  private BoardPost findPost(Long postId) {
    return postRepository.findById(postId)
        .orElseThrow(() -> new BoardNotFoundException("존재하지 않는 게시글입니다."));
  }
}
