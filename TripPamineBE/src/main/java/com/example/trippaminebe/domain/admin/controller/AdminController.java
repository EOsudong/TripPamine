package com.example.trippaminebe.domain.admin.controller;

import com.example.trippaminebe.domain.admin.dto.request.AdminLoginRequestDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminLoginResponseDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminResponseDto;
import com.example.trippaminebe.domain.admin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 관리자 관련 API의 진입점. Service만 호출하고,
// 실제 로직(검증, DB 조회 등)은 전부 AdminServiceImpl에 위임함
@RestController
@RequestMapping("/admin") // 공통 URI
@RequiredArgsConstructor
public class AdminController {

  private final AdminService adminService;

  // 관리자 로그인 - SecurityConfig에서 permitAll 처리된 유일한 admin 경로
  @PostMapping("/auth/login")
  @Operation(summary = "관리자 로그인")
  public ResponseEntity<AdminLoginResponseDto> login(@RequestBody AdminLoginRequestDto request) {
    AdminLoginResponseDto response = adminService.login(request);
    return ResponseEntity.ok(response);
  }

  // 관리자 프로필 조회 (내 정보 확인용) - 로그인 후 발급받은 토큰이 있어야 호출 가능
  @GetMapping("/{adminId}")
  @Operation(summary = "관리자 프로필 조회")
  public ResponseEntity<AdminResponseDto> getAdminProfile(@PathVariable Long adminId) {
    AdminResponseDto response = adminService.getAdminProfile(adminId);
    return ResponseEntity.ok(response);
  }
}