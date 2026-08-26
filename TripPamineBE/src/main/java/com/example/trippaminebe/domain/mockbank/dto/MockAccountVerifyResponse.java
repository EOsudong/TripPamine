package com.example.trippaminebe.domain.mockbank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mock 은행 쪽에 별도 DB 테이블(예전의 MockBankLedger)이 없으므로, 이 DTO는 항상
 * MockBankService.verifyAccount()가 결정론적으로 계산한 값으로 직접 builder를 통해
 * 만들어진다 (엔티티로부터 변환하는 from() 메서드가 필요 없음).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockAccountVerifyResponse {
  private String fintechUseNum;
  private String bankCode;
  private String bankName;
  private String accountHolderName;
  private BigDecimal balance;
}
