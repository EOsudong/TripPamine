package com.example.trippaminebe.domain.board.controller;

import com.example.trippaminebe.domain.admin.service.custom.CustomAdminDetails;
import com.example.trippaminebe.domain.board.dto.request.AdminBoardPostRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardCommentRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardReportProcessRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardPinRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardSearchType;
import com.example.trippaminebe.domain.board.dto.request.BoardStatusUpdateRequest;
import com.example.trippaminebe.domain.board.dto.response.AdminBoardReportResponse;
import com.example.trippaminebe.domain.board.dto.response.AdminReportedPostResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardCommentResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPageResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPostDetailResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPostSummaryResponse;
import com.example.trippaminebe.domain.board.entity.BoardCategory;
import com.example.trippaminebe.domain.board.entity.BoardReportStatus;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import com.example.trippaminebe.domain.board.service.AdminBoardReportService;
import com.example.trippaminebe.domain.board.service.AdminBoardService;
import com.example.trippaminebe.security.jwt.aspect.AdminLoggable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// 관리자용 게시판 API (/admin/** → AdminJWTAuthenticationFilter로 관리자 인증)
// 변경 작업은 @AdminLoggable로 ADMIN_LOGS에 자동 기록된다.
@Tag(name = "Admin Board", description = "게시판 관리 (관리자용)")
@RestController
@RequestMapping("/admin/boards")
@RequiredArgsConstructor
public class AdminBoardController {

  private final AdminBoardService adminBoardService;
  private final AdminBoardReportService adminBoardReportService;

  @GetMapping
  @Operation(summary = "게시글 목록 조회 (관리자용, 숨김/삭제 포함)", description = "status를 비우면 전체 상태")
  public ResponseEntity<BoardPageResponse<BoardPostSummaryResponse>> getPosts(
      @RequestParam(required = false) BoardCategory category,
      @RequestParam(required = false) BoardStatus status,
      @RequestParam(required = false) BoardSearchType searchType,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return ResponseEntity.ok(adminBoardService.getPosts(category, status, searchType, keyword, page, size));
  }

  @GetMapping("/{postId}")
  @Operation(summary = "게시글 상세 조회 (관리자용)")
  public ResponseEntity<BoardPostDetailResponse> getPost(@PathVariable Long postId) {
    return ResponseEntity.ok(adminBoardService.getPost(postId));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "게시글(공지) 작성 (관리자용)", description = "multipart: request(JSON) + images(파일, 최대 5장)")
  @AdminLoggable(actionType = "BOARD_CREATE", targetTable = "BOARD_POSTS")
  public ResponseEntity<BoardPostDetailResponse> createPost(
      @AuthenticationPrincipal CustomAdminDetails adminDetails,
      @Valid @RequestPart("request") AdminBoardPostRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> images
  ) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminBoardService.createPost(adminDetails.getAdmin().getId(), request, images));
  }

  @PutMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "게시글 수정 (관리자 작성 글만)")
  @AdminLoggable(actionType = "BOARD_UPDATE", targetTable = "BOARD_POSTS")
  public ResponseEntity<BoardPostDetailResponse> updatePost(
      @PathVariable Long postId,
      @Valid @RequestPart("request") AdminBoardPostRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> images
  ) {
    return ResponseEntity.ok(adminBoardService.updatePost(postId, request, images));
  }

  @PatchMapping("/{postId}/status")
  @Operation(summary = "게시글 상태 변경 (노출/숨김/삭제)")
  @AdminLoggable(actionType = "BOARD_STATUS_CHANGE", targetTable = "BOARD_POSTS")
  public ResponseEntity<Void> changePostStatus(
      @PathVariable Long postId,
      @Valid @RequestBody BoardStatusUpdateRequest request
  ) {
    adminBoardService.changePostStatus(postId, request.getStatus());
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{postId}/pin")
  @Operation(summary = "게시글 상단 고정/해제")
  @AdminLoggable(actionType = "BOARD_PIN_CHANGE", targetTable = "BOARD_POSTS")
  public ResponseEntity<Void> changePinned(
      @PathVariable Long postId,
      @RequestBody BoardPinRequest request
  ) {
    adminBoardService.changePinned(postId, request.isPinned());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{postId}/comments")
  @Operation(summary = "댓글 목록 조회 (관리자용, 숨김/삭제 포함, 답글 중첩)")
  public ResponseEntity<BoardPageResponse<BoardCommentResponse>> getComments(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomAdminDetails adminDetails,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size
  ) {
    return ResponseEntity.ok(adminBoardService.getComments(adminDetails.getAdmin().getId(), postId, page, size));
  }

  @PostMapping("/{postId}/comments")
  @Operation(summary = "운영자 댓글 작성")
  @AdminLoggable(actionType = "BOARD_COMMENT_CREATE", targetTable = "BOARD_COMMENTS")
  public ResponseEntity<BoardCommentResponse> createComment(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomAdminDetails adminDetails,
      @Valid @RequestBody BoardCommentRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminBoardService.createComment(adminDetails.getAdmin().getId(), postId, request));
  }

  @PostMapping("/comments/{commentId}/replies")
  @Operation(summary = "댓글에 운영자 답글 작성", description = "답글에 답글을 달면 원댓글 아래로 붙는다 (1단계 구조)")
  @AdminLoggable(actionType = "BOARD_COMMENT_REPLY", targetTable = "BOARD_COMMENTS")
  public ResponseEntity<BoardCommentResponse> replyToComment(
      @PathVariable Long commentId,
      @AuthenticationPrincipal CustomAdminDetails adminDetails,
      @Valid @RequestBody BoardCommentRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(adminBoardService.replyToComment(adminDetails.getAdmin().getId(), commentId, request));
  }

  @PutMapping("/comments/{commentId}")
  @Operation(summary = "운영자 댓글/답글 수정 (본인 작성분만)")
  @AdminLoggable(actionType = "BOARD_COMMENT_UPDATE", targetTable = "BOARD_COMMENTS")
  public ResponseEntity<BoardCommentResponse> updateComment(
      @PathVariable Long commentId,
      @AuthenticationPrincipal CustomAdminDetails adminDetails,
      @Valid @RequestBody BoardCommentRequest request
  ) {
    return ResponseEntity.ok(adminBoardService.updateComment(adminDetails.getAdmin().getId(), commentId, request));
  }

  @PatchMapping("/comments/{commentId}/status")
  @Operation(summary = "댓글 상태 변경 (노출/숨김/삭제)")
  @AdminLoggable(actionType = "BOARD_COMMENT_STATUS_CHANGE", targetTable = "BOARD_COMMENTS")
  public ResponseEntity<Void> changeCommentStatus(
      @PathVariable Long commentId,
      @Valid @RequestBody BoardStatusUpdateRequest request
  ) {
    adminBoardService.changeCommentStatus(commentId, request.getStatus());
    return ResponseEntity.noContent().build();
  }

  // ===================== 신고 관리 =====================

  @GetMapping("/reports")
  @Operation(summary = "신고된 게시글 목록 (게시글 단위 집계)", description = "status 미지정 시 처리 상태와 무관하게 전체")
  public ResponseEntity<BoardPageResponse<AdminReportedPostResponse>> getReportedPosts(
      @RequestParam(required = false) BoardReportStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return ResponseEntity.ok(adminBoardReportService.getReportedPosts(status, page, size));
  }

  @GetMapping("/reports/pending-count")
  @Operation(summary = "처리 대기 신고 수")
  public ResponseEntity<Map<String, Long>> getPendingReportCount() {
    return ResponseEntity.ok(Map.of("pendingCount", adminBoardReportService.countPending()));
  }

  @GetMapping("/{postId}/reports")
  @Operation(summary = "게시글별 신고 내역")
  public ResponseEntity<BoardPageResponse<AdminBoardReportResponse>> getPostReports(
      @PathVariable Long postId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size
  ) {
    return ResponseEntity.ok(adminBoardReportService.getPostReports(postId, page, size));
  }

  @PatchMapping("/{postId}/reports")
  @Operation(summary = "게시글 신고 일괄 처리", description = "대기 신고 전체를 ACCEPTED/REJECTED 처리 + 게시글 조치(KEEP/HIDE/DELETE/ACTIVATE)")
  @AdminLoggable(actionType = "BOARD_REPORT_PROCESS", targetTable = "BOARD_REPORTS")
  public ResponseEntity<Map<String, Integer>> processPostReports(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomAdminDetails adminDetails,
      @Valid @RequestBody BoardReportProcessRequest request
  ) {
    int processed = adminBoardReportService.processPostReports(adminDetails.getAdmin().getId(), postId, request);
    return ResponseEntity.ok(Map.of("processedCount", processed));
  }
}
