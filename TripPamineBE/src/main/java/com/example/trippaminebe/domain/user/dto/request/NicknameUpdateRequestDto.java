package com.example.trippaminebe.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// [닉네임 변경] PATCH /users/me/nickname 요청 바디.
// 형식 검증(2~12자, 한글·영문·숫자·밑줄)과 중복 검사는 UserServiceImpl.changeNickname()에서 한다.
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "닉네임 변경 요청 DTO")
public class NicknameUpdateRequestDto {

  @Schema(description = "새 닉네임 (2~12자, 한글·영문·숫자·밑줄)", example = "도파민여행자")
  private String userName;
}
