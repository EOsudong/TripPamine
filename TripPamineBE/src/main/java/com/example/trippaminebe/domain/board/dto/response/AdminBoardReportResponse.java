package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.board.entity.BoardReport;
import com.example.trippaminebe.domain.board.entity.BoardReportReason;
import com.example.trippaminebe.domain.board.entity.BoardReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 관리자용 개별 신고 응답
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "신고 응답 DTO (관리자용)")
public class AdminBoardReportResponse {

  private Long reportId;
  private Long postId;
  private Long reporterId;
  private String reporterName;
  private BoardReportReason reason;
  private String reasonLabel;
  private String detail;
  private BoardReportStatus status;
  private String handledByName;
  private LocalDateTime handledAt;
  private String handleMemo;
  private LocalDateTime createDate;

  public static AdminBoardReportResponse from(BoardReport report) {
    BoardWriter reporter = BoardWriter.of(report.getReporter(), null);
    return AdminBoardReportResponse.builder()
        .reportId(report.getReportId())
        .postId(report.getPost().getPostId())
        .reporterId(reporter.writerId())
        .reporterName(reporter.writerName())
        .reason(report.getReason())
        .reasonLabel(report.getReason().getLabel())
        .detail(report.getDetail())
        .status(report.getStatus())
        .handledByName(report.getHandledBy() != null ? report.getHandledBy().getAdminName() : null)
        .handledAt(report.getHandledAt())
        .handleMemo(report.getHandleMemo())
        .createDate(report.getCreateDate())
        .build();
  }
}
