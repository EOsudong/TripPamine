package com.example.trippaminebe.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "회원 정지 요청 DTO")
public class UserSuspendRequestDto {

  @NotBlank(message = "정지 사유를 입력해주세요.")
  @Schema(description = "정지 사유", example = "부적절한 게시물 반복 등록")
  private String reason;
}