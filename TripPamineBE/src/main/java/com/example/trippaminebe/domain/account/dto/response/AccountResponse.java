package com.example.trippaminebe.domain.account.dto.response;

import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.entity.LinkStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class AccountResponse {

  private Long accountId;              // 계좌연동일련번호
  private String bankCode;             // 은행/기관 코드
  private String bankName;             // 은행/기관명
  private String maskedAccountNumber;  // 계좌번호 마스킹 처리된 값 (뒤 4자리만 노출, 전체 번호는 응답에 절대 안 담음)
  private String accountAlias;         // 계좌별칭
  private LinkStatus linkStatus;       // 연동상태 (ACTIVE/INACTIVE)
  private LocalDateTime linkDate;      // 최초연동일시
  private BigDecimal balance;          // [Mock 은행 연동 추가] 현재 잔액 (연동 시 발급값 + 가계부 입력 반영 누계)

  // Entity -> Response 변환. 계좌번호는 mask()를 거쳐서만 담기 때문에 원본 번호가 응답에 노출될 일이 없음
  public static AccountResponse from(Account account) {
    return AccountResponse.builder()
        .accountId(account.getAccountId())
        .bankCode(account.getBankCode())
        .bankName(account.getBankName())
        .maskedAccountNumber(mask(account.getAccountNumber()))
        .accountAlias(account.getAccountAlias())
        .linkStatus(account.getLinkStatus())
        .linkDate(account.getLinkDate())
        .balance(account.getBalance())
        .build();
  }

  // 계좌번호 뒤 4자리만 남기고 나머지는 '*'로 가림 (예: "***********1234")
  private static String mask(String accountNumber) {
    if (accountNumber == null || accountNumber.length() <= 4) {
      return "****";
    }
    int visibleLength = 4;
    String tail = accountNumber.substring(accountNumber.length() - visibleLength);
    return "*".repeat(accountNumber.length() - visibleLength) + tail;
  }
}
