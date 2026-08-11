package com.example.trippaminebe.domain.admin.service.custom;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.admin.entity.AdminStatus;
import com.example.trippaminebe.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// Spring Security가 인증(로그인 검증)할 때 자동으로 호출하는 서비스.
@Service
@RequiredArgsConstructor
public class CustomAdminDetailService implements UserDetailsService {
  private final AdminRepository adminRepository;

  @Override
  public UserDetails loadUserByUsername(String adminLoginId) throws UsernameNotFoundException {
    // 로그인 아이디로 관리자 조회, 없으면 인증 실패 처리
    Admin admin = adminRepository
        .findByAdminLoginId(adminLoginId)
        .orElseThrow(() -> new UsernameNotFoundException(adminLoginId + " 관리자를 찾을 수 없습니다"));

    // 정지된 계정은 조회는 되더라도 인증은 실패 처리
    if (admin.getStatus() == AdminStatus.SUSPENDED) {
      throw new UsernameNotFoundException("정지된 관리자 계정입니다.");
    }
    return new CustomAdminDetails(admin);
  }
}