package com.example.trippaminebe.domain.board.entity;

import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// BOARD_POST_LIKES: 게시글 좋아요. (POST_ID, USER_ID) 유니크 제약으로 회원당 1회만 가능.
@Entity
@Table(
    name = "BOARD_POST_LIKES",
    uniqueConstraints = @UniqueConstraint(name = "UK_BOARD_POST_LIKES", columnNames = {"POST_ID", "USER_ID"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardPostLike {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BOARD_POST_LIKES_GEN")
  @SequenceGenerator(name = "SEQ_BOARD_POST_LIKES_GEN", sequenceName = "SEQ_BOARD_POST_LIKES", allocationSize = 1)
  @Column(name = "LIKE_ID")
  private Long likeId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "POST_ID", nullable = false)
  private BoardPost post;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID", nullable = false)
  private User user;

  @Column(name = "CREATE_DATE", nullable = false, updatable = false)
  private LocalDateTime createDate;

  @PrePersist
  protected void onCreate() {
    this.createDate = LocalDateTime.now();
  }
}
