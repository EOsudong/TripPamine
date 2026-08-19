package com.example.trippaminebe.domain.account.controller;

import com.example.trippaminebe.domain.account.dto.request.AccountAliasUpdateRequest;
import com.example.trippaminebe.domain.account.dto.request.AccountLinkRequest;
import com.example.trippaminebe.domain.account.dto.response.AccountHistoryResponse;
import com.example.trippaminebe.domain.account.dto.response.AccountResponse;
import com.example.trippaminebe.domain.account.service.AccountService;
import com.example.trippaminebe.domain.user.service.custom.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [수정한 부분]
 * 기존에는 @RequestHeader("X-USER-ID")로 사용자를 임시로 받고 있었는데
 * User/Admin/TravelPlan 컨트롤러와 동일하게
 * @AuthenticationPrincipal CustomUserDetails 방식으로 통일함.
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  // 계좌 등록 (사용자가 직접 입력한 계좌정보를 그대로 저장)
  @PostMapping
  @Operation(summary = "계좌 등록")
  public ResponseEntity<AccountResponse> linkAccount(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody AccountLinkRequest request
  ) {
    Long userId = userDetails.getUser().getId();
    AccountResponse response = accountService.linkAccount(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // 내 계좌 목록 조회
  @GetMapping
  @Operation(summary = "내 계좌 목록 조회")
  public ResponseEntity<List<AccountResponse>> getMyAccounts(
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    Long userId = userDetails.getUser().getId();
    return ResponseEntity.ok(accountService.getMyAccounts(userId));
  }

  // 계좌 별칭 수정
  @PatchMapping("/{accountId}")
  @Operation(summary = "계좌 별칭 수정")
  public ResponseEntity<AccountResponse> updateAlias(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long accountId,
      @Valid @RequestBody AccountAliasUpdateRequest request
  ) {
    Long userId = userDetails.getUser().getId();
    return ResponseEntity.ok(accountService.updateAlias(userId, accountId, request));
  }

  // 계좌 삭제(해지)
  @DeleteMapping("/{accountId}")
  @Operation(summary = "계좌 삭제")
  public ResponseEntity<Void> unlinkAccount(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long accountId
  ) {
    Long userId = userDetails.getUser().getId();
    accountService.unlinkAccount(userId, accountId);
    return ResponseEntity.noContent().build();
  }

  // 계좌 거래내역 조회 (페이징)
  @GetMapping("/{accountId}/history")
  @Operation(summary = "계좌 거래내역 조회")
  public ResponseEntity<Page<AccountHistoryResponse>> getHistory(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable Long accountId,
      Pageable pageable
  ) {
    Long userId = userDetails.getUser().getId();
    return ResponseEntity.ok(accountService.getHistory(userId, accountId, pageable));
  }
}
