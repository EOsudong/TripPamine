package com.example.trippaminebe.domain.board.entity;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// BOARD_POSTS: 게시글. 공지(관리자 작성)와 커뮤니티 글(회원 작성)을 CATEGORY로 구분해 한 테이블에서 관리.
// 작성자는 USER_ID 또는 ADMIN_ID 중 하나만 채워진다.
@Entity
@Table(name = "BOARD_POSTS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardPost {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BOARD_POSTS_GEN")
  @SequenceGenerator(name = "SEQ_BOARD_POSTS_GEN", sequenceName = "SEQ_BOARD_POSTS", allocationSize = 1)
  @Column(name = "POST_ID")
  private Long postId;

  @Enumerated(EnumType.STRING)
  @Column(name = "CATEGORY", nullable = false, length = 20)
  private BoardCategory category;

  @Column(name = "TITLE", nullable = false, length = 200)
  private String title;

  @Lob
  @Column(name = "CONTENT", nullable = false)
  private String content;

  // 회원 작성 글이면 채워짐
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "USER_ID")
  private User user;

  // 관리자 작성 글(공지 등)이면 채워짐
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ADMIN_ID")
  private Admin admin;

  // 조회수/좋아요수/댓글수는 목록 화면에서 매번 COUNT 하지 않도록 비정규화해서 저장.
  // 값 변경은 동시성 문제를 피하기 위해 BoardPostRepository의 UPDATE 쿼리(+1/-1)로만 한다.
  @Column(name = "VIEW_COUNT", nullable = false)
  @Builder.Default
  private Long viewCount = 0L;

  @Column(name = "LIKE_COUNT", nullable = false)
  @Builder.Default
  private Long likeCount = 0L;

  @Column(name = "COMMENT_COUNT", nullable = false)
  @Builder.Default
  private Long commentCount = 0L;

  // 누적 신고 수 (관리자 화면 표시용). 증감은 BoardPostRepository.addReportCount로만.
  @Column(name = "REPORT_COUNT", nullable = false)
  @Builder.Default
  private Long reportCount = 0L;

  // 상단 고정 여부(Y/N) - 관리자만 변경 가능
  @Column(name = "PINNED_YN", nullable = false, length = 1)
  @Builder.Default
  private String pinnedYn = "N";

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", nullable = false, length = 20)
  @Builder.Default
  private BoardStatus status = BoardStatus.ACTIVE;

  @Column(name = "CREATE_DATE", nullable = false, updatable = false)
  private LocalDateTime createDate;

  @Column(name = "UPDATE_DATE")
  private LocalDateTime updateDate;

  @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  @Builder.Default
  private List<BoardPostImage> images = new ArrayList<>();

  @PrePersist
  protected void onCreate() {
    this.createDate = LocalDateTime.now();
  }

  // ===== 도메인 메서드 =====

  public void update(BoardCategory category, String title, String content) {
    this.category = category;
    this.title = title;
    this.content = content;
    this.updateDate = LocalDateTime.now();
  }

  public void changeStatus(BoardStatus status) {
    this.status = status;
    this.updateDate = LocalDateTime.now();
  }

  public void changePinned(boolean pinned) {
    this.pinnedYn = pinned ? "Y" : "N";
  }

  public boolean isPinned() {
    return "Y".equals(pinnedYn);
  }

  public boolean isActive() {
    return status == BoardStatus.ACTIVE;
  }

  public BoardWriterType getWriterType() {
    return admin != null ? BoardWriterType.ADMIN : BoardWriterType.USER;
  }

  // 회원 작성 글이고, 그 작성자가 userId인지
  public boolean isWrittenBy(Long userId) {
    return userId != null && user != null && userId.equals(user.getId());
  }

  // JPA 연관관계 편의 메서드(1:N 양방향)
  public void addImage(BoardPostImage image) {
    this.images.add(image);
    image.assignPost(this);
  }

  public void removeImage(BoardPostImage image) {
    this.images.remove(image); // orphanRemoval = true 이므로 DB에서도 삭제됨
  }
}



