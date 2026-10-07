package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.board.entity.BoardComment;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "댓글 응답 DTO")
public class BoardCommentResponse {

  private Long commentId;
  private Long postId;
  private String content;
  private Long writerId;
  private String writerName;
  private String writerProfileImageUrl;
  private boolean mine;
  private BoardStatus status;
  private LocalDateTime createDate;
  private LocalDateTime updateDate;

  public static BoardCommentResponse of(BoardComment comment, Long currentUserId) {
    BoardWriter writer = BoardWriter.of(comment.getUser(), null);
    return BoardCommentResponse.builder()
        .commentId(comment.getCommentId())
        .postId(comment.getPost().getPostId())
        .content(comment.getContent())
        .writerId(writer.writerId())
        .writerName(writer.writerName())
        .writerProfileImageUrl(writer.profileImageUrl())
        .mine(comment.isWrittenBy(currentUserId))
        .status(comment.getStatus())
        .createDate(comment.getCreateDate())
        .updateDate(comment.getUpdateDate())
        .build();
  }
}
