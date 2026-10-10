package com.example.trippaminebe.domain.board.entity;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// BOARD_REPORTS: 게시글 신고. (POST_ID, REPORTER_ID) 유니크 제약으로 회원당 같은 글은 한 번만 신고 가능.
@Entity
@Table(
    name = "BOARD_REPORTS",
    uniqueConstraints = @UniqueConstraint(name = "UK_BOARD_REPORTS", columnNames = {"POST_ID", "REPORTER_ID"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BoardReport {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "SEQ_BOARD_REPORTS_GEN")
  @SequenceGenerator(name = "SEQ_BOARD_REPORTS_GEN", sequenceName = "SEQ_BOARD_REPORTS", allocationSize = 1)
  @Column(name = "REPORT_ID")
  private Long reportId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "POST_ID", nullable = false)
  private BoardPost post;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "REPORTER_ID", nullable = false)
  private User reporter;

  @Enumerated(EnumType.STRING)
  @Column(name = "REASON", nullable = false, length = 20)
  private BoardReportReason reason;

  @Column(name = "DETAIL", length = 500)
  private String detail;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", nullable = false, length = 20)
  @Builder.Default
  private BoardReportStatus status = BoardReportStatus.PENDING;

  // 처리한 관리자 (처리 전에는 null)
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "HANDLED_BY")
  private Admin handledBy;

  @Column(name = "HANDLED_AT")
  private LocalDateTime handledAt;

  @Column(name = "HANDLE_MEMO", length = 500)
  private String handleMemo;

  @Column(name = "CREATE_DATE", nullable = false, updatable = false)
  private LocalDateTime createDate;

  @PrePersist
  protected void onCreate() {
    this.createDate = LocalDateTime.now();
  }

  public void handle(BoardReportStatus result, Admin admin, String memo) {
    this.status = result;
    this.handledBy = admin;
    this.handleMemo = memo;
    this.handledAt = LocalDateTime.now();
  }

  public boolean isPending() {
    return status == BoardReportStatus.PENDING;
  }
}
