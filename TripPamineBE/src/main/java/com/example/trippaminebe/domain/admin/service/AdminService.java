package com.example.trippaminebe.domain.admin.service;

import com.example.trippaminebe.domain.admin.dto.request.AdminLoginRequestDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminLoginResponseDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminResponseDto;

// 관리자 서비스 인터페이스.
public interface AdminService {

  // 관리자 로그인 - 아이디/비밀번호 검증 후 JWT 토큰 발급
  AdminLoginResponseDto login(AdminLoginRequestDto request);

  // 관리자 프로필 조회 - 내 정보 확인용
  AdminResponseDto getAdminProfile(Long adminId);

}