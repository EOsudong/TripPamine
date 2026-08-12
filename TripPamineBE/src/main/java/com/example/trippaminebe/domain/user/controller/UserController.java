package com.example.trippaminebe.domain.user.controller;

import com.example.trippaminebe.domain.user.dto.request.LoginRequestDto;
import com.example.trippaminebe.domain.user.dto.request.SignUpRequestDto;
import com.example.trippaminebe.domain.user.dto.response.LoginResponseDto;
import com.example.trippaminebe.domain.user.dto.response.SignUpResponseDto;
import com.example.trippaminebe.domain.user.service.UserServiceImpl;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetailService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")//공통 URI
@RequiredArgsConstructor

public class UserController {

  private final UserServiceImpl userService;

  // 회원가입
  @PostMapping("/auth/signup")
  @Operation(summary = "회원가입")
  public ResponseEntity<SignUpResponseDto> signUp(@RequestBody SignUpRequestDto signUpRequestDto) {
    SignUpResponseDto response = userService.register(signUpRequestDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // 로그인
  @PostMapping("/auth/login")
  @Operation(summary = "회원 로그인")
  public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto) {
    LoginResponseDto response = userService.login(loginRequestDto);
    return ResponseEntity.ok(response);
  }

  // 회원탈퇴
  @DeleteMapping("/auth/withdraw")
  @Operation(summary = "회원탈퇴")
  public ResponseEntity<Void> withdraw(@AuthenticationPrincipal CustomUserDetails userDetails) {
  // 토큰에서 추출된 인증된 사용자의 이메일을 서비스로 전달
    userService.withdraw(userDetails.getUser().getId());

    return ResponseEntity.noContent().build();
  }

  // 이메일 중복 확인
  @GetMapping("/auth/check-email")
  @Operation(summary = "이메일 중복 확인")
  public ResponseEntity<Boolean> checkEamil (@RequestParam String email){
    boolean available = userService.isEmailAvailable(email);
    return ResponseEntity.ok(available);
  }


}
