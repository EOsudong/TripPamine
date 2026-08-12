package com.example.trippaminebe.domain.admin.service;

import com.example.trippaminebe.domain.admin.dto.request.UserSuspendRequestDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminUserResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// 관리자가 "회원(User)"을 다루는 기능. AdminService(관리자 본인 로그인)와는
// 목적이 달라서 별도 서비스로 분리함
public interface AdminUserService {

  // 회원 목록 조회 (페이징)
  Page<AdminUserResponseDto> getUserList(Pageable pageable);

  // 회원 강제 정지
  void suspendUser(Long userId, UserSuspendRequestDto request, Long adminId);
}