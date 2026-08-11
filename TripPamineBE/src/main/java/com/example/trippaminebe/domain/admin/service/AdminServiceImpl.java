package com.example.trippaminebe.domain.admin.service;

import com.example.trippaminebe.domain.admin.dto.request.AdminLoginRequestDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminLoginResponseDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminResponseDto;
import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.admin.entity.AdminStatus;
import com.example.trippaminebe.domain.admin.repository.AdminRepository;
import com.example.trippaminebe.security.jwt.JWTUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 이메일→아이디 조회, 상태 확인, 비밀번호 검증, 토큰 발급
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;
  private final JWTUtils jwtUtils;

  // 관리자 로그인
  @Override
  @Transactional
  public AdminLoginResponseDto login(AdminLoginRequestDto request) {
    // 아이디 존재 여부 확인
    Admin admin = adminRepository.findByAdminLoginId(request.getAdminLoginId())
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자 아이디입니다."));

    // 계정 상태 확인 (정지된 계정은 로그인 자체를 막음)
    if (admin.getStatus() != AdminStatus.ACTIVE) {
      throw new IllegalArgumentException("로그인 할 수 없는 계정 상태입니다. 상태: " + admin.getStatus());
    }

    // 비밀번호 일치 검증 (암호화된 값끼리 비교)
    if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
      throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
    }

    // 최종 로그인 일시 갱신 (Entity 도메인 메서드 호출 -> Dirty Checking으로 자동 UPDATE)
    admin.updateLastLoginDate();

    // JWT AccessToken 발급
    String accessToken = jwtUtils.createAccessToken(admin, "ACTIVE");

    // DTO로 변환해서 반환 (비밀번호 등 민감정보 제외)
    return AdminLoginResponseDto.adminLoginResponseDto(admin, accessToken);
  }

  // 관리자 프로필 조회
  @Override
  public AdminResponseDto getAdminProfile(Long adminId) {
    Admin admin = adminRepository.findById(adminId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자입니다. ID: " + adminId));

    return AdminResponseDto.adminResponseDto(admin);
  }
}