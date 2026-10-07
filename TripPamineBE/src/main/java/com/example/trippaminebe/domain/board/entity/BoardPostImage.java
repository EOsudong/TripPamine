package com.example.trippaminebe.domain.board.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// BOARD_POST_IMAGES: 게시글 첨부 이미지. 실제 파일은 서버 디스크(app.board.upload-dir)에 저장하고
// DB에는 저장 파일명/원본 파일명/접근 URL만 기록한다.
@Entity
@Table(name = "BOARD_POST_IMAGES")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardPostImage {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BOARD_POST_IMAGES_GEN")
  @SequenceGenerator(name = "SEQ_BOARD_POST_IMAGES_GEN", sequenceName = "SEQ_BOARD_POST_IMAGES", allocationSize = 1)
  @Column(name = "IMAGE_ID")
  private Long imageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "POST_ID", nullable = false)
  private BoardPost post;

  @Column(name = "STORED_NAME", nullable = false, length = 100)
  private String storedName; // 서버에 저장된 파일명(UUID.확장자)

  @Column(name = "ORIGINAL_NAME", length = 255)
  private String originalName; // 사용자가 올린 원본 파일명

  @Column(name = "IMAGE_URL", nullable = false, length = 500)
  private String imageUrl; // 프론트가 <img src>로 쓰는 경로 (예: /board-images/xxx.jpg)

  @Column(name = "SORT_ORDER", nullable = false)
  private Integer sortOrder;

  @Column(name = "CREATE_DATE", nullable = false, updatable = false)
  private LocalDateTime createDate;

  @PrePersist
  protected void onCreate() {
    this.createDate = LocalDateTime.now();
  }

  void assignPost(BoardPost post) {
    this.post = post;
  }

  public void changeSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
