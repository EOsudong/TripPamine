package com.example.trippaminebe.domain.user.service.custom;

import com.example.trippaminebe.domain.user.entity.User;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

  private User user;

  public CustomUserDetails(User user) {
    this.user = user;
  }

  public String getEmail() {
    return user.getEmail();
  }


  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    // 권한 설정 (기본USER)
    return Collections.emptyList();
  }

  @Override
  public @Nullable String getPassword() {
    return user.getPassword();
  }

  @Override
  public String getUsername() {
    return user.getUserName();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    // 탈퇴한 회원은 로그인/인증 불가하도록 처리
    return user.getStatus() != com.example.trippaminebe.domain.user.entity.UserStatus.WITHDRAW;
  }
}
