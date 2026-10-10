package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.board.entity.BoardComment;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import com.example.trippaminebe.domain.board.entity.BoardWriterType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 댓글 응답. 원댓글은 replies에 답글 목록을 담고, 답글은 replies가 비어 있다.
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "댓글 응답 DTO")
public class BoardCommentResponse {

  private Long commentId;
  private Long postId;
  private Long parentId; // 답글이면 원댓글 ID, 원댓글이면 null
  private String content;
  private Long writerId;
  private String writerName;
  private String writerProfileImageUrl;
  private BoardWriterType writerType;
  private boolean mine; // 회원 화면: 내 댓글 / 관리자 화면: 내가(현재 관리자) 쓴 운영자 댓글
  private BoardStatus status;
  private LocalDateTime createDate;
  private LocalDateTime updateDate;
  @Builder.Default
  private List<BoardCommentResponse> replies = new ArrayList<>();

  // 회원 화면용: 숨김/삭제된 원댓글은 작성자와 내용을 가린다 (답글만 남아 있는 경우에만 내려옴)
  public static BoardCommentResponse forUser(BoardComment comment, Long currentUserId) {
    if (!comment.isActive()) {
      return BoardCommentResponse.builder()
          .commentId(comment.getCommentId())
          .postId(comment.getPost().getPostId())
          .parentId(comment.isReply() ? comment.getParent().getCommentId() : null)
          .content(comment.getStatus() == BoardStatus.HIDDEN ? "운영자에 의해 숨겨진 댓글입니다." : "삭제된 댓글입니다.")
          .writerName("")
          .writerType(comment.getWriterType())
          .mine(false)
          .status(comment.getStatus())
          .createDate(comment.getCreateDate())
          .build();
    }
    return base(comment, comment.isWrittenBy(currentUserId));
  }

  // 관리자 화면용: 가리지 않고 그대로
  public static BoardCommentResponse forAdmin(BoardComment comment, Long currentAdminId) {
    return base(comment, comment.isWrittenByAdmin(currentAdminId));
  }

  private static BoardCommentResponse base(BoardComment comment, boolean mine) {
    BoardWriter writer = BoardWriter.of(comment.getUser(), comment.getAdmin());
    return BoardCommentResponse.builder()
        .commentId(comment.getCommentId())
        .postId(comment.getPost().getPostId())
        .parentId(comment.isReply() ? comment.getParent().getCommentId() : null)
        .content(comment.getContent())
        .writerId(writer.writerId())
        .writerName(writer.writerName())
        .writerProfileImageUrl(writer.profileImageUrl())
        .writerType(writer.writerType())
        .mine(mine)
        .status(comment.getStatus())
        .createDate(comment.getCreateDate())
        .updateDate(comment.getUpdateDate())
        .build();
  }
}
