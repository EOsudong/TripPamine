package com.example.trippaminebe.domain.user.service.custom;

import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {
  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository
        .findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException(email + "사용자를 찾을 수 없습니다"));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new UsernameNotFoundException(
          "탈퇴 또는 비활성화된 사용자입니다.");
    }
    return new CustomUserDetails(user);
  }
}
