package com.example.trippaminebe.domain.board.controller;

import com.example.trippaminebe.domain.board.dto.request.BoardCommentRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardPostRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardReportRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardSearchType;
import com.example.trippaminebe.domain.board.dto.response.*;
import com.example.trippaminebe.domain.board.entity.BoardCategory;
import com.example.trippaminebe.domain.board.service.BoardService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
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

// 회원용 게시판 API
// - GET 요청(목록/상세/댓글 목록)은 비로그인도 가능 (SecurityConfig에서 GET /boards/** permitAll)
//   로그인 상태로 요청하면 JWT 필터가 principal을 채워줘서 liked/mine 값이 계산된다.
// - 작성/수정/삭제/좋아요/댓글 작성은 로그인 필수
@Tag(name = "Board", description = "게시판 (공지/여행후기/질문/자유)")
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

  private final BoardService boardService;

  // 비로그인이면 null
  private Long currentUserId(CustomUserDetails userDetails) {
    return userDetails == null ? null : userDetails.getUser().getId();
  }

  @GetMapping
  @Operation(summary = "게시글 목록 조회", description = "?category=REVIEW&searchType=TITLE&keyword=제주&page=0&size=10")
  public ResponseEntity<BoardPageResponse<BoardPostSummaryResponse>> getPosts(
      @RequestParam(required = false) BoardCategory category,
      @RequestParam(required = false) BoardSearchType searchType,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size
  ) {
    return ResponseEntity.ok(boardService.getPosts(category, searchType, keyword, page, size));
  }

  @GetMapping("/{postId}")
  @Operation(summary = "게시글 상세 조회 (조회수 +1)")
  public ResponseEntity<BoardPostDetailResponse> getPost(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    return ResponseEntity.ok(boardService.getPost(postId, currentUserId(userDetails)));
  }

  @GetMapping("/{postId}/edit")
  @Operation(summary = "수정용 게시글 조회 (본인 글, 조회수 증가 없음)")
  public ResponseEntity<BoardPostDetailResponse> getPostForEdit(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    return ResponseEntity.ok(boardService.getPostForEdit(currentUserId(userDetails), postId));
  }

  // multipart/form-data
  //  - request : JSON (Content-Type: application/json) - BoardPostRequest
  //  - images  : 이미지 파일 0~5개 (선택)
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "게시글 작성", description = "multipart: request(JSON) + images(파일, 최대 5장)")
  public ResponseEntity<BoardPostDetailResponse> createPost(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestPart("request") BoardPostRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> images
  ) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(boardService.createPost(currentUserId(userDetails), request, images));
  }

  @PutMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "게시글 수정", description = "request.keepImageIds에 없는 기존 이미지는 삭제, images는 새로 추가")
  public ResponseEntity<BoardPostDetailResponse> updatePost(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestPart("request") BoardPostRequest request,
      @RequestPart(value = "images", required = false) List<MultipartFile> images
  ) {
    return ResponseEntity.ok(boardService.updatePost(currentUserId(userDetails), postId, request, images));
  }

  @DeleteMapping("/{postId}")
  @Operation(summary = "게시글 삭제 (본인 글)")
  public ResponseEntity<Void> deletePost(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    boardService.deletePost(currentUserId(userDetails), postId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{postId}/likes")
  @Operation(summary = "좋아요 토글", description = "누르지 않았으면 추가, 이미 눌렀으면 취소")
  public ResponseEntity<BoardLikeResponse> toggleLike(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    return ResponseEntity.ok(boardService.toggleLike(currentUserId(userDetails), postId));
  }

  @PostMapping("/{postId}/reports")
  @Operation(summary = "게시글 신고", description = "회원당 같은 글은 1회. 본인 글/운영자 글은 신고 불가. 사유 ETC는 detail 필수")
  public ResponseEntity<Void> reportPost(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody BoardReportRequest request
  ) {
    boardService.reportPost(currentUserId(userDetails), postId, request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  // ===================== 댓글 =====================

  @GetMapping("/{postId}/comments")
  @Operation(summary = "댓글 목록 조회")
  public ResponseEntity<BoardPageResponse<BoardCommentResponse>> getComments(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size
  ) {
    return ResponseEntity.ok(boardService.getComments(postId, currentUserId(userDetails), page, size));
  }

  @PostMapping("/{postId}/comments")
  @Operation(summary = "댓글 작성")
  public ResponseEntity<BoardCommentResponse> createComment(
      @PathVariable Long postId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody BoardCommentRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(boardService.createComment(currentUserId(userDetails), postId, request));
  }

  @PutMapping("/comments/{commentId}")
  @Operation(summary = "댓글 수정 (본인 댓글)")
  public ResponseEntity<BoardCommentResponse> updateComment(
      @PathVariable Long commentId,
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody BoardCommentRequest request
  ) {
    return ResponseEntity.ok(boardService.updateComment(currentUserId(userDetails), commentId, request));
  }

  @DeleteMapping("/comments/{commentId}")
  @Operation(summary = "댓글 삭제 (본인 댓글)")
  public ResponseEntity<Void> deleteComment(
      @PathVariable Long commentId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    boardService.deleteComment(currentUserId(userDetails), commentId);
    return ResponseEntity.noContent().build();
  }
}



