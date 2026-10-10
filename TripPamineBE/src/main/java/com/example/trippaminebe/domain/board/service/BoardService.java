package com.example.trippaminebe.domain.board.service;

import com.example.trippaminebe.domain.board.dto.request.BoardCommentRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardPostRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardReportRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardSearchType;
import com.example.trippaminebe.domain.board.dto.response.*;
import com.example.trippaminebe.domain.board.entity.*;
import com.example.trippaminebe.domain.board.exception.BoardAccessDeniedException;
import com.example.trippaminebe.domain.board.exception.BoardConflictException;
import com.example.trippaminebe.domain.board.exception.BoardNotFoundException;
import com.example.trippaminebe.domain.board.repository.BoardCommentRepository;
import com.example.trippaminebe.domain.board.repository.BoardPostLikeRepository;
import com.example.trippaminebe.domain.board.repository.BoardPostRepository;
import com.example.trippaminebe.domain.board.repository.BoardPostSpecs;
import com.example.trippaminebe.domain.board.repository.BoardReportRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

// 회원용 게시판 서비스 (목록/상세/작성/수정/삭제, 좋아요, 댓글, 신고)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

  static final int DEFAULT_PAGE_SIZE = 10;
  static final int MAX_PAGE_SIZE = 50;
  static final int DEFAULT_COMMENT_PAGE_SIZE = 20;

  private final BoardPostRepository postRepository;
  private final BoardCommentRepository commentRepository;
  private final BoardPostLikeRepository likeRepository;
  private final UserRepository userRepository;
  private final BoardPostImageHandler imageHandler;
  private final BoardReportRepository reportRepository;

  // 처리 대기(PENDING) 신고가 이 수 이상 쌓이면 관리자 확인 전까지 자동 숨김
  @Value("${app.board.report-hide-threshold:5}")
  private int reportHideThreshold;

  // ===================== 게시글 =====================

  // 목록: 노출(ACTIVE) 글만, 상단 고정 글 → 최신 글 순
  public BoardPageResponse<BoardPostSummaryResponse> getPosts(
      BoardCategory category, BoardSearchType searchType, String keyword, int page, int size
  ) {
    Page<BoardPost> result = postRepository.findAll(
        BoardPostSpecs.search(category, BoardStatus.ACTIVE, searchType, keyword),
        postPageable(page, size)
    );
    return BoardPageResponse.from(result.map(BoardPostSummaryResponse::from));
  }

  // 상세: 조회수 +1 후 반환 (userId는 비로그인이면 null)
  @Transactional
  public BoardPostDetailResponse getPost(Long postId, Long userId) {
    findActivePost(postId); // 숨김/삭제 글이면 조회수 올리기 전에 404
    postRepository.increaseViewCount(postId);

    BoardPost post = findActivePost(postId); // UPDATE 쿼리 이후 최신 값으로 다시 조회
    boolean liked = userId != null && likeRepository.existsByPost_PostIdAndUser_Id(postId, userId);
    boolean reported = userId != null && reportRepository.existsByPost_PostIdAndReporter_Id(postId, userId);
    return BoardPostDetailResponse.of(post, liked, post.isWrittenBy(userId), reported);
  }

  // 수정 화면 진입용: 본인 글인지 확인하고, 조회수는 올리지 않는다
  public BoardPostDetailResponse getPostForEdit(Long userId, Long postId) {
    findUser(userId);
    BoardPost post = findActivePost(postId);
    checkOwner(post, userId);
    boolean liked = likeRepository.existsByPost_PostIdAndUser_Id(postId, userId);
    return BoardPostDetailResponse.of(post, liked, true);
  }

  @Transactional
  public BoardPostDetailResponse createPost(Long userId, BoardPostRequest request, List<MultipartFile> images) {
    User user = findWritableUser(userId);
    checkUserWritableCategory(request.getCategory());

    BoardPost post = BoardPost.builder()
        .category(request.getCategory())
        .title(request.getTitle().trim())
        .content(request.getContent())
        .user(user)
        .build();
    imageHandler.attachNew(post, images);

    postRepository.save(post);
    return BoardPostDetailResponse.of(post, false, true);
  }

  @Transactional
  public BoardPostDetailResponse updatePost(Long userId, Long postId, BoardPostRequest request, List<MultipartFile> images) {
    findWritableUser(userId);
    BoardPost post = findActivePost(postId);
    checkOwner(post, userId);
    checkUserWritableCategory(request.getCategory());

    post.update(request.getCategory(), request.getTitle().trim(), request.getContent());
    imageHandler.sync(post, request.getKeepImageIds(), images);

    postRepository.flush(); // 새 이미지 ID를 응답에 담기 위해 즉시 반영
    boolean liked = likeRepository.existsByPost_PostIdAndUser_Id(postId, userId);
    return BoardPostDetailResponse.of(post, liked, true);
  }

  // 삭제는 소프트 삭제(STATUS=DELETED). 관리자 화면에서는 계속 확인 가능.
  @Transactional
  public void deletePost(Long userId, Long postId) {
    BoardPost post = findActivePost(postId);
    checkOwner(post, userId);
    post.changeStatus(BoardStatus.DELETED);
  }

  // ===================== 좋아요 =====================

  // 좋아요 토글: 안 눌렀으면 추가, 이미 눌렀으면 취소
  @Transactional
  public BoardLikeResponse toggleLike(Long userId, Long postId) {
    BoardPost post = findActivePost(postId);
    User user = findUser(userId);

    Optional<BoardPostLike> existing = likeRepository.findByPost_PostIdAndUser_Id(postId, userId);
    boolean liked;
    if (existing.isPresent()) {
      likeRepository.delete(existing.get());
      postRepository.addLikeCount(postId, -1);
      liked = false;
    } else {
      // saveAndFlush: 동시에 두 번 눌러 유니크 제약에 걸리면 여기서 바로 예외 → 409 응답
      likeRepository.saveAndFlush(BoardPostLike.builder().post(post).user(user).build());
      postRepository.addLikeCount(postId, 1);
      liked = true;
    }

    Long likeCount = postRepository.findLikeCount(postId).orElse(0L);
    return new BoardLikeResponse(liked, likeCount);
  }

  // ===================== 댓글 =====================

  // 원댓글 기준으로 페이징하고, 각 원댓글의 답글(운영자 답글 등)을 replies에 담아 내려준다
  public BoardPageResponse<BoardCommentResponse> getComments(Long postId, Long userId, int page, int size) {
    findActivePost(postId);
    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size, DEFAULT_COMMENT_PAGE_SIZE));
    Page<BoardComment> roots = commentRepository.findVisibleRoots(postId, BoardStatus.ACTIVE, pageable);

    List<Long> rootIds = roots.getContent().stream().map(BoardComment::getCommentId).toList();
    List<BoardComment> replies = rootIds.isEmpty()
        ? List.of()
        : commentRepository.findByParent_CommentIdInAndStatusOrderByCommentIdAsc(rootIds, BoardStatus.ACTIVE);

    List<BoardCommentResponse> content = BoardCommentTree.assemble(
        roots.getContent(), replies, comment -> BoardCommentResponse.forUser(comment, userId));
    return BoardPageResponse.from(new PageImpl<>(content, pageable, roots.getTotalElements()));
  }

  @Transactional
  public BoardCommentResponse createComment(Long userId, Long postId, BoardCommentRequest request) {
    User user = findWritableUser(userId);
    BoardPost post = findActivePost(postId);

    BoardComment comment = commentRepository.save(BoardComment.builder()
        .post(post)
        .user(user)
        .content(request.getContent().trim())
        .build());
    BoardCommentResponse response = BoardCommentResponse.forUser(comment, userId);

    postRepository.addCommentCount(postId, 1);
    return response;
  }

  @Transactional
  public BoardCommentResponse updateComment(Long userId, Long commentId, BoardCommentRequest request) {
    findWritableUser(userId);
    BoardComment comment = findActiveComment(commentId);
    if (!comment.isWrittenBy(userId)) {
      throw new BoardAccessDeniedException("본인이 작성한 댓글만 수정할 수 있습니다.");
    }
    comment.updateContent(request.getContent().trim());
    return BoardCommentResponse.forUser(comment, userId);
  }

  @Transactional
  public void deleteComment(Long userId, Long commentId) {
    BoardComment comment = findActiveComment(commentId);
    if (!comment.isWrittenBy(userId)) {
      throw new BoardAccessDeniedException("본인이 작성한 댓글만 삭제할 수 있습니다.");
    }
    Long postId = comment.getPost().getPostId();
    comment.changeStatus(BoardStatus.DELETED);
    postRepository.addCommentCount(postId, -1);
  }

  // ===================== 신고 =====================

  // 게시글 신고. 회원당 같은 글은 한 번만, 본인 글/운영자 글은 신고할 수 없다.
  @Transactional
  public void reportPost(Long userId, Long postId, BoardReportRequest request) {
    User reporter = findWritableUser(userId);
    BoardPost post = findActivePost(postId);

    if (post.getAdmin() != null) {
      throw new BoardAccessDeniedException("운영자가 작성한 글은 신고할 수 없습니다.");
    }
    if (post.isWrittenBy(userId)) {
      throw new BoardAccessDeniedException("본인이 작성한 글은 신고할 수 없습니다.");
    }
    if (request.getReason() == BoardReportReason.ETC && !StringUtils.hasText(request.getDetail())) {
      throw new IllegalArgumentException("기타 사유는 상세 내용을 입력해주세요.");
    }
    if (reportRepository.existsByPost_PostIdAndReporter_Id(postId, userId)) {
      throw new BoardConflictException("이미 신고한 게시글입니다.");
    }

    // saveAndFlush: 동시에 두 번 신고해서 유니크 제약(UK_BOARD_REPORTS)에 걸리면 여기서 바로 409
    reportRepository.saveAndFlush(BoardReport.builder()
        .post(post)
        .reporter(reporter)
        .reason(request.getReason())
        .detail(StringUtils.hasText(request.getDetail()) ? request.getDetail().trim() : null)
        .build());
    postRepository.increaseReportCount(postId);

    long pending = reportRepository.countByPost_PostIdAndStatus(postId, BoardReportStatus.PENDING);
    if (reportHideThreshold > 0 && pending >= reportHideThreshold) {
      // increaseReportCount가 영속성 컨텍스트를 비웠으므로 다시 조회해서 변경
      postRepository.findById(postId)
          .filter(BoardPost::isActive)
          .ifPresent(p -> {
            p.changeStatus(BoardStatus.HIDDEN);
            log.info("[BOARD] 신고 {}건 누적으로 게시글 자동 숨김: postId={}", pending, postId);
          });
    }
  }

  // ===================== 내부 공통 =====================

  private BoardPost findActivePost(Long postId) {
    return postRepository.findById(postId)
        .filter(BoardPost::isActive)
        .orElseThrow(() -> new BoardNotFoundException("존재하지 않거나 삭제된 게시글입니다."));
  }

  private BoardComment findActiveComment(Long commentId) {
    return commentRepository.findById(commentId)
        .filter(BoardComment::isActive)
        .filter(comment -> comment.getPost().isActive())
        .orElseThrow(() -> new BoardNotFoundException("존재하지 않거나 삭제된 댓글입니다."));
  }

  private User findUser(Long userId) {
    if (userId == null) {
      throw new BoardAccessDeniedException("로그인이 필요합니다.");
    }
    return userRepository.findById(userId)
        .orElseThrow(() -> new BoardAccessDeniedException("존재하지 않는 회원입니다."));
  }

  // 글/댓글 작성·수정은 활동중(ACTIVE) 회원만 가능 (정지/휴면 회원 차단)
  private User findWritableUser(Long userId) {
    User user = findUser(userId);
    if (user.getStatus() == UserStatus.SUSPENDED) {
      throw new BoardAccessDeniedException("정지된 회원은 글을 작성할 수 없습니다.");
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new BoardAccessDeniedException("활동 중인 회원만 글을 작성할 수 있습니다.");
    }
    return user;
  }

  private void checkOwner(BoardPost post, Long userId) {
    if (!post.isWrittenBy(userId)) {
      throw new BoardAccessDeniedException("본인이 작성한 글만 수정/삭제할 수 있습니다.");
    }
  }

  private void checkUserWritableCategory(BoardCategory category) {
    if (!category.isUserWritable()) {
      throw new BoardAccessDeniedException(category.getLabel() + "은(는) 관리자만 작성할 수 있습니다.");
    }
  }

  static Pageable postPageable(int page, int size) {
    return PageRequest.of(
        Math.max(page, 0),
        clampSize(size, DEFAULT_PAGE_SIZE),
        Sort.by(Sort.Order.desc("pinnedYn"), Sort.Order.desc("postId"))
    );
  }

  static int clampSize(int size, int defaultSize) {
    if (size <= 0) return defaultSize;
    return Math.min(size, MAX_PAGE_SIZE);
  }
}



