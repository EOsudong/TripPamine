package com.example.trippaminebe.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AdminLoginRequest {

  @NotBlank(message = "관리자 아이디는 필수 입력값입니다.")
  private String adminLoginId; // 로그인 시 입력하는 관리자 아이디 (ADMIN_LOGIN_ID와 매칭)

  @NotBlank(message = "비밀번호는 필수 입력값입니다.")
  private String password; // 로그인 시 입력하는 비밀번호 (암호화 전 원문, 서버에서 검증용으로만 사용)
}