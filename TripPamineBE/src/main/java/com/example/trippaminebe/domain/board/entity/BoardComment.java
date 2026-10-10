package com.example.trippaminebe.domain.board.entity;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// BOARD_COMMENTS: 게시글 댓글.
// - 작성자는 회원(USER_ID) 또는 관리자(ADMIN_ID) 중 하나
// - PARENT_ID가 있으면 답글. 답글은 한 단계만 허용 (답글의 답글은 원댓글 아래로 붙인다)
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

  // 회원 댓글이면 채워짐
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID")
  private User user;

  // 관리자(운영자) 댓글/답글이면 채워짐
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ADMIN_ID")
  private Admin admin;

  // 답글이면 원댓글
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "PARENT_ID")
  private BoardComment parent;

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

  public boolean isReply() {
    return parent != null;
  }

  public boolean isWrittenBy(Long userId) {
    return userId != null && user != null && userId.equals(user.getId());
  }

  public boolean isWrittenByAdmin(Long adminId) {
    return adminId != null && admin != null && adminId.equals(admin.getId());
  }

  public BoardWriterType getWriterType() {
    return admin != null ? BoardWriterType.ADMIN : BoardWriterType.USER;
  }
}
