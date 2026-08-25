package com.example.trippaminebe.domain.mockbank.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mock 오픈뱅킹 서버의 "계좌 실명확인 + 핀테크이용번호 발급" 요청 DTO.
 * 실제 금융결제원 오픈뱅킹의 "계좌 실명조회" API 요청과 비슷한 최소 형태로 구성함.
 */
@Getter
@Setter
@NoArgsConstructor
public class MockAccountVerifyRequest {

  // bankCode는 앱 쪽 AccountLinkRequest와 마찬가지로 선택 입력 (프론트가 은행명만 받고
  // 코드는 안 받는 화면이다)
  private String bankCode;

  @NotBlank
  private String bankName;

  @NotBlank
  private String accountNumber;

  // 예금주명 - 없으면 Mock 서버가 계좌번호를 바탕으로 임의 생성함
  private String accountHolderName;

  public MockAccountVerifyRequest(String bankCode, String bankName, String accountNumber, String accountHolderName) {
    this.bankCode = bankCode;
    this.bankName = bankName;
    this.accountNumber = accountNumber;
    this.accountHolderName = accountHolderName;
  }
}
