package com.example.trippaminebe.domain.board.service;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.admin.repository.AdminRepository;
import com.example.trippaminebe.domain.board.dto.request.AdminBoardPostRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardCommentRequest;
import com.example.trippaminebe.domain.board.dto.request.BoardSearchType;
import com.example.trippaminebe.domain.board.dto.response.BoardCommentResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPageResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPostDetailResponse;
import com.example.trippaminebe.domain.board.dto.response.BoardPostSummaryResponse;
import com.example.trippaminebe.domain.board.entity.BoardCategory;
import com.example.trippaminebe.domain.board.entity.BoardComment;
import com.example.trippaminebe.domain.board.entity.BoardPost;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import com.example.trippaminebe.domain.board.exception.BoardAccessDeniedException;
import com.example.trippaminebe.domain.board.exception.BoardNotFoundException;
import com.example.trippaminebe.domain.board.repository.BoardCommentRepository;
import com.example.trippaminebe.domain.board.repository.BoardPostRepository;
import com.example.trippaminebe.domain.board.repository.BoardPostSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// 관리자용 게시판 서비스: 공지 작성, 전체 글(숨김/삭제 포함) 조회, 숨김/삭제/복구, 상단 고정,
// 댓글 관리 + 운영자 댓글/답글 작성
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminBoardService {

  private final BoardPostRepository postRepository;
  private final BoardCommentRepository commentRepository;
  private final AdminRepository adminRepository;
  private final BoardPostImageHandler imageHandler;

  // status가 null이면 전체 상태
  public BoardPageResponse<BoardPostSummaryResponse> getPosts(
      BoardCategory category, BoardStatus status, BoardSearchType searchType, String keyword, int page, int size
  ) {
    return BoardPageResponse.from(postRepository.findAll(
        BoardPostSpecs.search(category, status, searchType, keyword),
        BoardService.postPageable(page, size)
    ).map(BoardPostSummaryResponse::fromForAdmin));
  }

  // 관리자 상세 조회는 조회수를 올리지 않는다
  public BoardPostDetailResponse getPost(Long postId) {
    return BoardPostDetailResponse.of(findPost(postId), false, false);
  }

  @Transactional
  public BoardPostDetailResponse createPost(Long adminId, AdminBoardPostRequest request, List<MultipartFile> images) {
    Admin admin = adminRepository.findById(adminId)
        .orElseThrow(() -> new BoardAccessDeniedException("존재하지 않는 관리자입니다."));

    BoardPost post = BoardPost.builder()
        .category(request.getCategory())
        .title(request.getTitle().trim())
        .content(request.getContent())
        .admin(admin)
        .pinnedYn(request.isPinned() ? "Y" : "N")
        .build();
    imageHandler.attachNew(post, images);

    postRepository.save(post);
    return BoardPostDetailResponse.of(post, false, true);
  }

  // 관리자가 작성한 글만 수정 가능 (회원 글은 내용 수정 대신 숨김/삭제로 관리)
  @Transactional
  public BoardPostDetailResponse updatePost(Long postId, AdminBoardPostRequest request, List<MultipartFile> images) {
    BoardPost post = findPost(postId);
    if (post.getAdmin() == null) {
      throw new BoardAccessDeniedException("회원이 작성한 글은 수정할 수 없습니다. 숨김/삭제 처리를 이용해주세요.");
    }
    post.update(request.getCategory(), request.getTitle().trim(), request.getContent());
    post.changePinned(request.isPinned());
    imageHandler.sync(post, request.getKeepImageIds(), images);

    postRepository.flush();
    return BoardPostDetailResponse.of(post, false, true);
  }

  @Transactional
  public void changePostStatus(Long postId, BoardStatus status) {
    findPost(postId).changeStatus(status);
  }

  @Transactional
  public void changePinned(Long postId, boolean pinned) {
    findPost(postId).changePinned(pinned);
  }

  // 원댓글 기준 페이징 + 답글 중첩. 숨김/삭제 포함 전체를 그대로 보여준다.
  public BoardPageResponse<BoardCommentResponse> getComments(Long adminId, Long postId, int page, int size) {
    findPost(postId);
    Pageable pageable = PageRequest.of(Math.max(page, 0), BoardService.clampSize(size, BoardService.DEFAULT_COMMENT_PAGE_SIZE));
    Page<BoardComment> roots = commentRepository.findByPost_PostIdAndParentIsNullOrderByCommentIdAsc(postId, pageable);

    List<Long> rootIds = roots.getContent().stream().map(BoardComment::getCommentId).toList();
    List<BoardComment> replies = rootIds.isEmpty()
        ? List.of()
        : commentRepository.findByParent_CommentIdInOrderByCommentIdAsc(rootIds);

    List<BoardCommentResponse> content = BoardCommentTree.assemble(
        roots.getContent(), replies, comment -> BoardCommentResponse.forAdmin(comment, adminId));
    return BoardPageResponse.from(new PageImpl<>(content, pageable, roots.getTotalElements()));
  }

  // 게시글에 운영자 댓글 작성 (원댓글)
  @Transactional
  public BoardCommentResponse createComment(Long adminId, Long postId, BoardCommentRequest request) {
    BoardPost post = findCommentablePost(postId);
    return saveAdminComment(findAdmin(adminId), post, null, request.getContent());
  }

  // 댓글에 운영자 답글 작성. 답글에 답글을 달면 원댓글 아래로 붙인다 (한 단계 구조 유지)
  @Transactional
  public BoardCommentResponse replyToComment(Long adminId, Long commentId, BoardCommentRequest request) {
    BoardComment target = findComment(commentId);
    BoardComment root = target.isReply() ? target.getParent() : target;
    BoardPost post = findCommentablePost(root.getPost().getPostId());
    return saveAdminComment(findAdmin(adminId), post, root, request.getContent());
  }

  // 본인이 쓴 운영자 댓글/답글 수정
  @Transactional
  public BoardCommentResponse updateComment(Long adminId, Long commentId, BoardCommentRequest request) {
    BoardComment comment = findComment(commentId);
    if (!comment.isWrittenByAdmin(adminId)) {
      throw new BoardAccessDeniedException("본인이 작성한 운영자 댓글만 수정할 수 있습니다.");
    }
    comment.updateContent(request.getContent().trim());
    return BoardCommentResponse.forAdmin(comment, adminId);
  }

  private BoardCommentResponse saveAdminComment(Admin admin, BoardPost post, BoardComment parent, String content) {
    BoardComment comment = commentRepository.save(BoardComment.builder()
        .post(post)
        .admin(admin)
        .parent(parent)
        .content(content.trim())
        .build());
    BoardCommentResponse response = BoardCommentResponse.forAdmin(comment, admin.getId());

    postRepository.addCommentCount(post.getPostId(), 1);
    return response;
  }

  private BoardPost findCommentablePost(Long postId) {
    BoardPost post = findPost(postId);
    if (post.getStatus() == BoardStatus.DELETED) {
      throw new BoardAccessDeniedException("삭제된 게시글에는 댓글을 작성할 수 없습니다.");
    }
    return post;
  }

  private BoardComment findComment(Long commentId) {
    return commentRepository.findById(commentId)
        .orElseThrow(() -> new BoardNotFoundException("존재하지 않는 댓글입니다."));
  }

  private Admin findAdmin(Long adminId) {
    return adminRepository.findById(adminId)
        .orElseThrow(() -> new BoardAccessDeniedException("존재하지 않는 관리자입니다."));
  }

  // 댓글 상태 변경 시 게시글의 COMMENT_COUNT(노출 댓글 수)도 함께 맞춰준다
  @Transactional
  public void changeCommentStatus(Long commentId, BoardStatus status) {
    BoardComment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new BoardNotFoundException("존재하지 않는 댓글입니다."));

    boolean wasActive = comment.isActive();
    comment.changeStatus(status);
    boolean nowActive = comment.isActive();

    if (wasActive != nowActive) {
      postRepository.addCommentCount(comment.getPost().getPostId(), nowActive ? 1 : -1);
    }
  }

  private BoardPost findPost(Long postId) {
    return postRepository.findById(postId)
        .orElseThrow(() -> new BoardNotFoundException("존재하지 않는 게시글입니다."));
  }
}



