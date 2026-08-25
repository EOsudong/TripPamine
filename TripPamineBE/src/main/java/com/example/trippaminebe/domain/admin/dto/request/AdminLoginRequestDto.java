package com.example.trippaminebe.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 관리자 로그인 요청 DTO.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "관리자 로그인 요청 DTO")
public class AdminLoginRequestDto {

  @NotBlank(message = "관리자 아이디를 입력해주세요.")
  @Schema(description = "관리자 로그인 아이디", example = "admin")
  private String adminLoginId; // 로그인 시 입력하는 관리자 아이디 (ADMIN_LOGIN_ID와 매칭)

  @NotBlank(message = "비밀번호를 입력해주세요.")
  @Schema(description = "비밀번호", example = "admin1234")
  private String password; // 로그인 시 입력하는 비밀번호 (암호화 전 원문, 서버에서 검증용으로만 사용)
}