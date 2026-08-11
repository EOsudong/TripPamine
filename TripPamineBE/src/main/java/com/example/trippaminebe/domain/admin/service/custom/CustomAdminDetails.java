package com.example.trippaminebe.domain.admin.service.custom;

import com.example.trippaminebe.domain.admin.entity.Admin;
import com.example.trippaminebe.domain.admin.entity.AdminStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Spring Security가 인증된 관리자 정보를 다루는 표준 형식(UserDetails)에 Admin 엔티티를 담아서 쓸 수 있게 하는 래퍼 클래스.
// getAuthorities()에서 실제 권한(SUPER/STAFF)을 반환한다
@Getter
public class CustomAdminDetails implements UserDetails {

  private final Admin admin;

  public CustomAdminDetails(Admin admin) {
    this.admin = admin;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // ADMIN_ROLE 값을 "ROLE_SUPER" 또는 "ROLE_STAFF" 형태의 권한으로 변환해서 반환
    return List.of(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()));
  }

  @Override
  public String getPassword() {
    return admin.getPassword(); // 암호화된 비밀번호 (Spring Security가 로그인 시 이 값과 입력값을 비교)
  }

  @Override
  public String getUsername() {
    return admin.getAdminLoginId(); // Spring Security 입장에서의 "아이디" - 실제로는 관리자 로그인 아이디
  }

  @Override
  public boolean isAccountNonExpired() {
    return true; // 계정 만료 기능은 아직 사용 안 함 (항상 true)
  }

  @Override
  public boolean isAccountNonLocked() {
    return true; // 계정 잠금 기능은 아직 사용 안 함 (항상 true)
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true; // 비밀번호 만료 기능은 아직 사용 안 함 (항상 true)
  }

  @Override
  public boolean isEnabled() {
    // 정지(SUSPENDED)된 관리자는 로그인/인증 자체가 불가능하도록 처리
    return admin.getStatus() != AdminStatus.SUSPENDED;
  }
}