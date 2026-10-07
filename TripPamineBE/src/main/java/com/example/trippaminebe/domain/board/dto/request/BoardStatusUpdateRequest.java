package com.example.trippaminebe.domain.board.dto.request;

import com.example.trippaminebe.domain.board.entity.BoardStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// 관리자 게시글/댓글 상태 변경 요청 (ACTIVE / HIDDEN / DELETED)
@Getter
@Setter
@Schema(description = "게시글/댓글 상태 변경 요청 DTO (관리자용)")
public class BoardStatusUpdateRequest {

  @NotNull(message = "변경할 상태를 선택해주세요.")
  @Schema(description = "상태", example = "HIDDEN")
  private BoardStatus status;
}
