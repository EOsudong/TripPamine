package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.board.entity.BoardCategory;
import com.example.trippaminebe.domain.board.entity.BoardPost;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import com.example.trippaminebe.domain.board.entity.BoardWriterType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// 게시글 상세 응답
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "게시글 상세 응답 DTO")
public class BoardPostDetailResponse {

  private Long postId;
  private BoardCategory category;
  private String categoryLabel;
  private String title;
  private String content;
  private Long writerId;
  private String writerName;
  private String writerProfileImageUrl;
  private BoardWriterType writerType;
  private Long viewCount;
  private Long likeCount;
  private Long commentCount;
  private boolean pinned;
  private BoardStatus status;
  private List<BoardImageResponse> images;
  private boolean liked; // 로그인한 회원이 좋아요를 눌렀는지 (비로그인은 false)
  private boolean mine;  // 로그인한 회원 본인 글인지 (수정/삭제 버튼 노출용)
  private LocalDateTime createDate;
  private LocalDateTime updateDate;

  public static BoardPostDetailResponse of(BoardPost post, boolean liked, boolean mine) {
    BoardWriter writer = BoardWriter.of(post.getUser(), post.getAdmin());
    return BoardPostDetailResponse.builder()
        .postId(post.getPostId())
        .category(post.getCategory())
        .categoryLabel(post.getCategory().getLabel())
        .title(post.getTitle())
        .content(post.getContent())
        .writerId(writer.writerId())
        .writerName(writer.writerName())
        .writerProfileImageUrl(writer.profileImageUrl())
        .writerType(writer.writerType())
        .viewCount(post.getViewCount())
        .likeCount(post.getLikeCount())
        .commentCount(post.getCommentCount())
        .pinned(post.isPinned())
        .status(post.getStatus())
        .images(post.getImages().stream().map(BoardImageResponse::from).toList())
        .liked(liked)
        .mine(mine)
        .createDate(post.getCreateDate())
        .updateDate(post.getUpdateDate())
        .build();
  }
}
