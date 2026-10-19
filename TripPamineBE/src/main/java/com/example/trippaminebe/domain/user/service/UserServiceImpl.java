package com.example.trippaminebe.domain.user.service;

import com.example.trippaminebe.domain.user.dto.request.NicknameUpdateRequestDto;
import com.example.trippaminebe.domain.user.dto.response.NicknameCheckResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.regex.Pattern;


import com.example.trippaminebe.domain.user.dto.request.LoginRequestDto;
import com.example.trippaminebe.domain.user.dto.request.SignUpRequestDto;
import com.example.trippaminebe.domain.user.dto.request.UpdateRequestDto;
import com.example.trippaminebe.domain.user.dto.response.LoginResponseDto;
import com.example.trippaminebe.domain.user.dto.response.SignUpResponseDto;
import com.example.trippaminebe.domain.user.dto.response.UserResponseDto;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.entity.UserStatus;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import com.example.trippaminebe.domain.user.repository.UserSocialAccountRepository;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import com.example.trippaminebe.security.jwt.JWTUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
  private final UserSocialAccountRepository userSocialAccountRepository;

  private final PasswordEncoder passwordEncoder;
  private final JWTUtils jwtUtils;
  private final AuthenticationManager authenticationManager;

  //로그인
  @Override
  @Transactional
  public LoginResponseDto login(LoginRequestDto request) {
    // 1. Spring Security에게 이메일 + 비밀번호 인증 요청
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.getEmail(),
            request.getPassword()
        )
    );

    // 2. 인증 성공한 사용자 가져오기
    CustomUserDetails userDetails =
        (CustomUserDetails) authentication.getPrincipal();

    User user = userDetails.getUser();

    // 계정 상태 확인 (휴면, 탈퇴, 정지 계정 로그인 방지)
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new IllegalArgumentException(
          "로그인 할 수 없는 계정 상태입니다. 상태: " + user.getStatus());
    }

    // 4. 마지막 로그인 시간 업데이트
    user.updateLastLoginDate();

    // 5. JWT AccessToken 발급
    String accessToken = jwtUtils.createAccessToken(user, "ACTIVE");

    // 6. 로그인 응답 DTO 반환
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

  // 회원정보 조회
  @Override
  public UserResponseDto getUserInfo(Long userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

    return UserResponseDto.userResponse(user);
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
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id: " + userId));

    // 이미 탈퇴한 회원인지 검증
    if (user.getStatus() == UserStatus.WITHDRAW) {
      throw new IllegalArgumentException("이미 탈퇴 처리된 회원입니다.");
    }

    // 소프트 삭제로 실행 (Dirty Checking)
    user.withdraw();
  }

  // 이메일 중복 확인
  @Override
  public Boolean isEmailAvailable(String email) {
    return !userRepository.existsByEmail(email);
  }

  // =====================================================
  // [닉네임 변경]
  // 규칙: 앞뒤 공백 제거 후 2~12자, 한글·영문·숫자·밑줄(_)만 허용.
  // (가입 때는 형식 검사가 없었으므로, 기존 닉네임은 그대로 두고 "변경할 때"만 이 규칙을 적용한다)
  // =====================================================
  private static final int NICKNAME_MIN = 2;
  private static final int NICKNAME_MAX = 12;
  private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9_]+$");

  // 형식이 잘못됐으면 안내 문구, 정상이면 null
  private String validateNicknameFormat(String userName) {
    if (userName == null || userName.isBlank()) {
      return "닉네임을 입력해주세요.";
    }
    if (userName.length() < NICKNAME_MIN || userName.length() > NICKNAME_MAX) {
      return "닉네임은 " + NICKNAME_MIN + "~" + NICKNAME_MAX + "자로 입력해주세요.";
    }
    if (!NICKNAME_PATTERN.matcher(userName).matches()) {
      return "닉네임은 한글, 영문, 숫자, 밑줄(_)만 사용할 수 있어요.";
    }
    return null;
  }

  @Override
  @Transactional(readOnly = true)
  public NicknameCheckResponseDto checkNickname(String userName, Long userId) {
    String trimmed = userName == null ? null : userName.trim();

    String formatError = validateNicknameFormat(trimmed);
    if (formatError != null) {
      return NicknameCheckResponseDto.of(false, formatError);
    }

    boolean taken = userId != null
        ? userRepository.existsByUserNameAndIdNot(trimmed, userId)
        : userRepository.existsByUserName(trimmed);
    if (taken) {
      return NicknameCheckResponseDto.of(false, "이미 사용 중인 닉네임이에요.");
    }
    return NicknameCheckResponseDto.of(true, "사용할 수 있는 닉네임이에요.");
  }

  @Override
  @Transactional
  public UserResponseDto changeNickname(Long userId, NicknameUpdateRequestDto request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

    // 정지·휴면·탈퇴 회원은 변경 불가 (게시판 작성 제한과 같은 기준)
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new IllegalArgumentException("활동 중인 회원만 닉네임을 변경할 수 있어요.");
    }

    String newUserName = request.getUserName() == null ? null : request.getUserName().trim();

    String formatError = validateNicknameFormat(newUserName);
    if (formatError != null) {
      throw new IllegalArgumentException(formatError);
    }

    // 지금과 같은 닉네임이면 아무것도 바꾸지 않고 그대로 돌려준다
    if (newUserName.equals(user.getUserName())) {
      return UserResponseDto.userResponse(user);
    }

    if (userRepository.existsByUserNameAndIdNot(newUserName, userId)) {
      throw new IllegalArgumentException("이미 사용 중인 닉네임이에요.");
    }

    user.changeUserName(newUserName);

    // 두 사람이 거의 동시에 같은 닉네임으로 바꾸면 위 중복 검사를 둘 다 통과할 수 있다.
    // USER_NAME의 UNIQUE 제약이 최종 방어선이 되므로, 여기서 바로 flush해서 위반을 400 안내로 바꾼다.
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException("이미 사용 중인 닉네임이에요.");
    }

    return UserResponseDto.userResponse(user);
  }


}