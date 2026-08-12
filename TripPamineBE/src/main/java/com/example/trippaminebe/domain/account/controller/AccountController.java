package com.example.trippaminebe.domain.account.controller;

import com.example.trippaminebe.domain.account.dto.request.AccountAliasUpdateRequest;
import com.example.trippaminebe.domain.account.dto.request.AccountLinkRequest;
import com.example.trippaminebe.domain.account.dto.response.AccountHistoryResponse;
import com.example.trippaminebe.domain.account.dto.response.AccountResponse;
import com.example.trippaminebe.domain.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 주의: 로그인한 사용자의 userId를 어떻게 꺼내는지는 project의 security 설정에 따라 다름.
 * 지금은 @RequestHeader("X-USER-ID") 로 임시 대체해둔 상태이고,
 * 실제로는 SecurityContext에서 인증된 사용자 정보를 꺼내는 방식(예: @AuthenticationPrincipal
 * CustomUserDetails userDetails)으로 교체해야 함. domain/security 패키지 구현 확인 후 수정 필요.
 * (User/Admin 컨트롤러는 이미 @AuthenticationPrincipal 방식을 쓰고 있으니, 그 패턴으로 맞추면 됨)
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  // 계좌 연동 등록
  @PostMapping
  @Operation(summary = "계좌 연동 등록")
  public ResponseEntity<AccountResponse> linkAccount(
      @RequestHeader("X-USER-ID") Long userId, // TODO: 실제 인증 방식으로 교체
      @Valid @RequestBody AccountLinkRequest request
  ) {
    AccountResponse response = accountService.linkAccount(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // 내 계좌 목록 조회
  @GetMapping
  @Operation(summary = "내 계좌 목록 조회")
  public ResponseEntity<List<AccountResponse>> getMyAccounts(
      @RequestHeader("X-USER-ID") Long userId // TODO: 실제 인증 방식으로 교체
  ) {
    return ResponseEntity.ok(accountService.getMyAccounts(userId));
  }

  // 계좌 별칭 수정
  @PatchMapping("/{accountId}")
  @Operation(summary = "계좌 별칭 수정")
  public ResponseEntity<AccountResponse> updateAlias(
      @RequestHeader("X-USER-ID") Long userId, // TODO: 실제 인증 방식으로 교체
      @PathVariable Long accountId,
      @Valid @RequestBody AccountAliasUpdateRequest request
  ) {
    return ResponseEntity.ok(accountService.updateAlias(userId, accountId, request));
  }

  // 계좌 연동 해지
  @DeleteMapping("/{accountId}")
  @Operation(summary = "계좌 연동 해지")
  public ResponseEntity<Void> unlinkAccount(
      @RequestHeader("X-USER-ID") Long userId, // TODO: 실제 인증 방식으로 교체
      @PathVariable Long accountId
  ) {
    accountService.unlinkAccount(userId, accountId);
    return ResponseEntity.noContent().build();
  }

  // 계좌 거래내역 조회 (페이징)
  @GetMapping("/{accountId}/history")
  @Operation(summary = "계좌 거래내역 조회")
  public ResponseEntity<Page<AccountHistoryResponse>> getHistory(
      @RequestHeader("X-USER-ID") Long userId, // TODO: 실제 인증 방식으로 교체
      @PathVariable Long accountId,
      Pageable pageable
  ) {
    return ResponseEntity.ok(accountService.getHistory(userId, accountId, pageable));
  }
}