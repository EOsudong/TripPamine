package com.example.trippaminebe.domain.admin.dto.response;

import com.example.trippaminebe.domain.admin.entity.Admin;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 관리자 로그인 성공 시 반환하는 DTO. — 토큰 정보만 담고,
// 관리자 개인정보(이름, 이메일 등)는 여기 안 담아서 최소한만 노출함
@Getter
@Builder
@AllArgsConstructor
@Schema(description = "관리자 JWT 토큰 응답 DTO")
public class AdminLoginResponseDto {
  private String adminLoginId; // 로그인한 관리자 아이디 (누가 로그인했는지 프론트에서 바로 확인용)
  private String accessToken;  // 발급된 JWT 액세스 토큰 - 이후 요청 시 Authorization 헤더에 실어 보냄

  @Builder.Default
  private String tokenType = "Bearer"; // 토큰 타입 고정값

  // Admin 엔티티 + 발급된 토큰 -> 응답 DTO로 변환
  public static AdminLoginResponseDto adminLoginResponseDto(Admin admin, String accessToken) {
    return AdminLoginResponseDto.builder()
        .adminLoginId(admin.getAdminLoginId())
        .accessToken(accessToken)
        .build();
  }
}