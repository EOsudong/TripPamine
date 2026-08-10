package com.example.trippaminebe.domain.admin.service;


import com.example.trippaminebe.domain.admin.dto.request.AdminLoginRequest;
import com.example.trippaminebe.domain.admin.dto.response.AdminResponse;
import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
//import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

  // 리포지토리 주입
  private final AdminRepository adminRepository;

//  private final PasswordEncoder passwordEncoder; // Spring Security 적용 시

  // 관리자 로그인
/*  @Transactional
  public AdminResponse login(AdminLoginRequest request) {

    // 1. 로그인 아이디로 관리자 조회
    Admin admin = adminRepository.findByAdminLoginId(request.getAdminLoginId())
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자입니다. ID: " + request.getAdminLoginId()));

    // 2. 비밀번호 일치 여부 검증
    if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
      throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
    }

    // 3. 최종 로그인 시각 갱신 (Dirty Checking 적용)
    admin.updateLastLoginDate();

    return AdminResponse.adminResponse(admin);
  }*/


  // 관리자 프로필 조회
  public AdminResponse getAdminProfile(Long adminId) {
    Admin admin = adminRepository.findById(adminId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 관리자입니다. ID: " + adminId));

    return AdminResponse.adminResponse(admin);
  }

}