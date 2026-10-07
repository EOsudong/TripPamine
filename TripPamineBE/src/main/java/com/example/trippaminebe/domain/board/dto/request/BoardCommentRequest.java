package com.example.trippaminebe.domain.board.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "댓글 작성/수정 요청 DTO")
public class BoardCommentRequest {

  @NotBlank(message = "댓글 내용을 입력해주세요.")
  @Size(max = 1000, message = "댓글은 1,000자 이하로 입력해주세요.")
  @Schema(description = "댓글 내용", example = "좋은 정보 감사합니다!")
  private String content;
}
