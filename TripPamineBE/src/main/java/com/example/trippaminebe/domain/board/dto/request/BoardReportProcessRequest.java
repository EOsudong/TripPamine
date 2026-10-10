package com.example.trippaminebe.domain.board.dto.request;

import com.example.trippaminebe.domain.board.entity.BoardReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 관리자 신고 처리 요청: 한 게시글에 쌓인 대기(PENDING) 신고를 한 번에 처리한다
@Getter
@Setter
@Schema(description = "신고 처리 요청 DTO (관리자용)")
public class BoardReportProcessRequest {

  // 처리 후 게시글을 어떻게 할지
  public enum PostAction {
    KEEP,     // 그대로 둠
    HIDE,     // 숨김
    DELETE,   // 삭제
    ACTIVATE  // 다시 노출 (자동 숨김된 글을 반려하며 복구할 때)
  }

  @NotNull(message = "처리 결과를 선택해주세요.")
  @Schema(description = "처리 결과 (ACCEPTED: 신고 인정 / REJECTED: 반려)", example = "ACCEPTED")
  private BoardReportStatus result;

  @Schema(description = "게시글 조치 (기본 KEEP)", example = "HIDE")
  private PostAction postAction = PostAction.KEEP;

  @Size(max = 500, message = "처리 메모는 500자 이하로 입력해주세요.")
  @Schema(description = "처리 메모", example = "광고성 게시글 확인")
  private String memo;
}
