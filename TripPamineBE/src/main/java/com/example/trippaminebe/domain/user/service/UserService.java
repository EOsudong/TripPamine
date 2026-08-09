package com.example.trippaminebe.domain.user.service;


import com.example.trippaminebe.domain.user.dto.request.UserSignUpRequest;
import com.example.trippaminebe.domain.user.dto.response.UserResponse;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  // 리포지토리 주입
  private final UserRepository userRepository;

//  private final PasswordEncoder passwordEncoder; // Spring Security 적용 시

  // 회원가입
/*  @Transactional
  public UserResponse signUp(UserSignUpRequest request) {

    // 1. 이메일, 닉네임 중복 검증
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
    }
    if (userRepository.existsByUserName(request.getUserName())) {
      throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
    }

    // 2. 비밀번호 암호화 및 Entity 생성
    String encodedPassword = passwordEncoder.encode(request.getPassword());

    User user = User.builder()
        .email(request.getEmail())
        .password(encodedPassword)
        .name(request.getName())
        .userName(request.getUserName())
        .phoneNumber(request.getPhoneNumber())
        .build();

    // 3. 저장
    User savedUser = userRepository.save(user);

    return UserResponse.userResponse(savedUser);
  }*/


  // 회원 프로필 조회
  public UserResponse getUserProfile(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. ID: " + userId));

    return UserResponse.userResponse(user);
  }


  // 회원 탈퇴 처리
  @Transactional
  public void withdrawUser(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. ID: " + userId));

    user.withdraw(); // Entity 내부 도메인 메서드 호출하여 상태 변경 (Dirty Checking 적용)
  }
}
