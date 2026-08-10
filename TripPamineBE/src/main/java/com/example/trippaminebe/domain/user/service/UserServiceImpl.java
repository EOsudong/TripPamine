package com.example.trippaminebe.domain.user.service;


import com.example.trippaminebe.domain.user.dto.request.LoginRequestDto;
import com.example.trippaminebe.domain.user.dto.request.SignUpRequestDto;
import com.example.trippaminebe.domain.user.dto.request.UpdateRequestDto;
import com.example.trippaminebe.domain.user.dto.response.LoginResponseDto;
import com.example.trippaminebe.domain.user.dto.response.SignUpResponseDto;
import com.example.trippaminebe.domain.user.dto.response.UserResponseDto;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import com.example.trippaminebe.security.jwt.JWTUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  //EntityManager 주입
  @PersistenceContext
  private final EntityManager entityManager;
  // 리포지토리 주입
  private final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder;
  private final JWTUtils jwtUtils;

  //로그인
  @Override
  @Transactional
  public LoginResponseDto login(LoginRequestDto request) {
    // 이메일 존재 여부 확인
    User user = userRepository.findByEmail(
            request.getEmail())
        .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일 입니다"));

    // 계정 상태 확인 (휴면, 탈퇴, 정지 계정 로그인 방지)
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new IllegalArgumentException("로그인 할 수 없는 계정 상태입니다. 상태: " + user.getStatus());
    }

    // 비밀번호 일치 검증
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
    }

    //최종 로그인 일시 최신화 (도메인 메서드 호출)
    user.updateLastLoginDate();

    // JWT AccessToken 발급
    String accessToken = jwtUtils.createAccessToken(user, "ACTIVE");

    // DTO 반환
    return LoginResponseDto.loginResponseDto(user, accessToken);
  }

  // 회원가입
  @Override
  @Transactional
  public SignUpResponseDto register(SignUpRequestDto request) {

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

    return SignUpResponseDto.signUpResponse(savedUser);
  }

  // 회원정보
  @Override
  public UserResponseDto getUserInfo(UserResponseDto userResponseDto) {
    return null;
  }

  @Override
  public UserResponseDto updateProfile(Long userId, UpdateRequestDto request) {
    return null;
  }

  // 회원탈퇴
  @Override
  @Transactional
  public void withdraw(Long userId) {
    // 회원 존재 여부 검증
    User user = userRepository.findById(userId.toString())
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id: " + userId));

    // 이미 탈퇴한 회원인지 검증
    if (user.getStatus() == UserStatus.WITHDRAW) {
      throw new IllegalArgumentException("이미 탈퇴 처리된 회원입니다.");
    }

    // 소프트 삭제로 실행 (Dirty Checking)
    user.withdraw();

  }


}
