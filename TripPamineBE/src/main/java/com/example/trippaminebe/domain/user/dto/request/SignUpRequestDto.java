package com.example.trippaminebe.domain.user.dto.request;

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
@Schema(description = "회원가입 요청 DTO")
public class SignUpRequestDto {

  @NotBlank(message = "이메일은 필수 입력값입니다.")
  @Email(message = "올바른 이메일 형식이 아닙니다.")
  @Schema(description = "사용자 이메일", example = "user@example.com")
  private String email;

  @NotBlank(message = "비밀번호는 필수 입력값입니다.")
  private String password;

  private String name;

  @NotBlank(message = "닉네임은 필수 입력값입니다.")
  @Schema(description = "사용자 닉네임", example = "여행가1")
  private String userName;

  private String phoneNumber;
}