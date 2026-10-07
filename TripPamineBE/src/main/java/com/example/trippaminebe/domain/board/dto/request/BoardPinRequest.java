package com.example.trippaminebe.domain.board.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "게시글 상단 고정 변경 요청 DTO (관리자용)")
public class BoardPinRequest {

  @Schema(description = "상단 고정 여부", example = "true")
  private boolean pinned;
}
