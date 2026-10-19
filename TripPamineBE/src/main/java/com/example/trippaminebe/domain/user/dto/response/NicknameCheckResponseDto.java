package com.example.trippaminebe.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

// [닉네임 변경] GET /users/auth/check-nickname 응답.
// 화면은 available로 저장 버튼 활성화를 정하고, message를 입력칸 아래에 그대로 보여준다.
@Getter
@AllArgsConstructor
@Schema(description = "닉네임 사용 가능 여부 응답 DTO")
public class NicknameCheckResponseDto {

  @Schema(description = "사용 가능 여부")
  private boolean available;

  @Schema(description = "안내 문구 (형식 오류 / 중복 / 사용 가능)")
  private String message;

  public static NicknameCheckResponseDto of(boolean available, String message) {
    return new NicknameCheckResponseDto(available, message);
  }
}
