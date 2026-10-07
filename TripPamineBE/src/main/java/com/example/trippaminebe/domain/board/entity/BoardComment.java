package com.example.trippaminebe.domain.board.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// BOARD_COMMENTS: 게시글 댓글 (회원만 작성, 관리자는 숨김/삭제로 관리)
@Entity
@Table(name = "BOARD_COMMENTS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardComment {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BOARD_COMMENTS_GEN")
  @SequenceGenerator(name = "SEQ_BOARD_COMMENTS_GEN", sequenceName = "SEQ_BOARD_COMMENTS", allocationSize = 1)
  @Column(name = "COMMENT_ID")
  private Long commentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "POST_ID", nullable = false)
  private BoardPost post;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID", nullable = false)
  private User user;

  @Column(name = "CONTENT", nullable = false, length = 1000)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", nullable = false, length = 20)
  @Builder.Default
  private BoardStatus status = BoardStatus.ACTIVE;

  @Column(name = "CREATE_DATE", nullable = false, updatable = false)
  private LocalDateTime createDate;

  @Column(name = "UPDATE_DATE")
  private LocalDateTime updateDate;

  @PrePersist
  protected void onCreate() {
    this.createDate = LocalDateTime.now();
  }

  public void updateContent(String content) {
    this.content = content;
    this.updateDate = LocalDateTime.now();
  }

  public void changeStatus(BoardStatus status) {
    this.status = status;
    this.updateDate = LocalDateTime.now();
  }

  public boolean isActive() {
    return status == BoardStatus.ACTIVE;
  }

  public boolean isWrittenBy(Long userId) {
    return userId != null && user != null && userId.equals(user.getId());
  }
}
