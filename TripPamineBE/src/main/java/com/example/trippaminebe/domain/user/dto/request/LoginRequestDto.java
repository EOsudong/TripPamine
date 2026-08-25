package com.example.trippaminebe.domain.user.dto.request;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "로그인 요청 DTO")
public class LoginRequestDto {

  @NotBlank(message = "이메일을 입력해주세요.")
  @Email
  @Schema(description = "사용자 이메일", example = "test1@trippamine.com")
  private String email;

  @NotBlank(message = "비밀번호를 입력해주세요.")
  @Schema(description = "비밀번호", example = "12345678")
  private String password;
}
