package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.board.entity.BoardCategory;
import com.example.trippaminebe.domain.board.entity.BoardPost;
import com.example.trippaminebe.domain.board.entity.BoardStatus;
import com.example.trippaminebe.domain.board.entity.BoardWriterType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 게시글 목록 한 줄 응답 (본문 제외)
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "게시글 목록 응답 DTO")
public class BoardPostSummaryResponse {

  private Long postId;
  private BoardCategory category;
  private String categoryLabel;
  private String title;
  private Long writerId;
  private String writerName;
  private BoardWriterType writerType;
  private Long viewCount;
  private Long likeCount;
  private Long commentCount;
  private boolean pinned;
  private String thumbnailUrl; // 첫 번째 첨부 이미지 (없으면 null)
  private int imageCount;
  // 누적 신고 수 - 관리자 목록(fromForAdmin)에서만 채워지고, 회원 응답에서는 JSON에서 빠진다
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long reportCount;
  private BoardStatus status;
  private LocalDateTime createDate;

  public static BoardPostSummaryResponse from(BoardPost post) {
    BoardWriter writer = BoardWriter.of(post.getUser(), post.getAdmin());
    return BoardPostSummaryResponse.builder()
        .postId(post.getPostId())
        .category(post.getCategory())
        .categoryLabel(post.getCategory().getLabel())
        .title(post.getTitle())
        .writerId(writer.writerId())
        .writerName(writer.writerName())
        .writerType(writer.writerType())
        .viewCount(post.getViewCount())
        .likeCount(post.getLikeCount())
        .commentCount(post.getCommentCount())
        .pinned(post.isPinned())
        .thumbnailUrl(post.getImages().isEmpty() ? null : post.getImages().get(0).getImageUrl())
        .imageCount(post.getImages().size())
        .status(post.getStatus())
        .createDate(post.getCreateDate())
        .build();
  }

  public static BoardPostSummaryResponse fromForAdmin(BoardPost post) {
    BoardPostSummaryResponse response = from(post);
    response.reportCount = post.getReportCount();
    return response;
  }
}
