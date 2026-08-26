package com.example.trippaminebe.domain.mockbank.controller;

import com.example.trippaminebe.domain.mockbank.dto.MockAccountVerifyRequest;
import com.example.trippaminebe.domain.mockbank.dto.MockAccountVerifyResponse;
import com.example.trippaminebe.domain.mockbank.service.MockBankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Mock 오픈뱅킹 서버" 엔드포인트.
 *
 * 실제 서비스 로직(AccountService)은 이 컨트롤러를 직접 호출하지 않고,
 * account.client.MockOpenBankingClient가 RestTemplate으로 이 컨트롤러를
 * 실제 외부 API인 것처럼 HTTP로 호출한다 (SecurityConfig에서 이 경로는
 * permitAll 처리되어 있다).
 *
 * 계좌 실명확인 엔드포인트 하나만 있으면 된다
 */
@Tag(name = "Mock Open Banking", description = "실제 은행/오픈뱅킹 API를 흉내내는 Mock 서버")
@RestController
@RequestMapping("/mock-bank")
@RequiredArgsConstructor
public class MockBankController {

  private final MockBankService mockBankService;

  // 계좌 실명확인 + 핀테크이용번호/최초 잔액 발급
  @PostMapping("/accounts/verify")
  @Operation(summary = "[Mock] 계좌 실명확인 및 잔액 발급")
  public ResponseEntity<MockAccountVerifyResponse> verifyAccount(
      @Valid @RequestBody MockAccountVerifyRequest request
  ) {
    return ResponseEntity.ok(mockBankService.verifyAccount(request));
  }
}
