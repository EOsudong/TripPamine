package com.example.trippaminebe.domain.board.dto.response;

import com.example.trippaminebe.domain.board.entity.BoardReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

// 관리자 "신고된 게시글" 목록 한 줄: 게시글 요약 + 신고 집계
@Getter
@AllArgsConstructor
@Schema(description = "신고된 게시글 응답 DTO (관리자용)")
public class AdminReportedPostResponse {

  private BoardPostSummaryResponse post;
  private long reportCount;   // 조회 조건(status)에 해당하는 신고 수
  private long pendingCount;  // 그중 처리 대기 신고 수
  private LocalDateTime lastReportedAt;
  private Map<BoardReportReason, Long> reasonCounts; // 사유별 건수
}
