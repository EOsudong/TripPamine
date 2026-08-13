package com.example.trippaminebe.domain.admin.controller;

import com.example.trippaminebe.domain.admin.dto.request.UserSuspendRequestDto;
import com.example.trippaminebe.domain.admin.dto.response.AdminUserResponseDto;
import com.example.trippaminebe.domain.admin.service.AdminUserService;
import com.example.trippaminebe.domain.admin.service.custom.CustomAdminDetails;
import com.example.trippaminebe.security.jwt.aspect.AdminLoggable;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// AdminController(관리자 본인 로그인/프로필)와 별개로, "관리자가 회원을 관리하는" API 전용 컨트롤러
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

  private final AdminUserService adminUserService;

  // 회원 목록 조회 - ?page=0&size=20 형태로 페이징 파라미터 사용 가능
  @GetMapping
  @Operation(summary = "회원 목록 조회 (관리자용)")
  public ResponseEntity<Page<AdminUserResponseDto>> getUserList(Pageable pageable) {
    return ResponseEntity.ok(adminUserService.getUserList(pageable));
  }

  // 회원 강제 정지 - @AdminLoggable을 붙여두면 AOP가 자동으로 ADMIN_LOGS에 기록함
  @PatchMapping("/{userId}/suspend")
  @Operation(summary = "회원 강제 정지 (관리자용)")
  @AdminLoggable(actionType = "USER_SUSPEND", targetTable = "USERS")
  public ResponseEntity<Void> suspendUser(
      @PathVariable Long userId,
      @RequestBody UserSuspendRequestDto request,
      @AuthenticationPrincipal CustomAdminDetails adminDetails
  ) {
    adminUserService.suspendUser(userId, request, adminDetails.getAdmin().getId());
    return ResponseEntity.noContent().build();
  }

  // 회원 정지 해제
  @PatchMapping("/{userId}/unsuspend")
  @Operation(summary = "회원 정지 해제 (관리자용)")
  @AdminLoggable(actionType = "USER_UNSUSPEND", targetTable = "USERS")
  public ResponseEntity<Void> unsuspendUser(@PathVariable Long userId) {
    adminUserService.unsuspendUser(userId);
    return ResponseEntity.noContent().build();
  }
}