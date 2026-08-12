package com.example.trippaminebe.domain.account.dto.response;

import com.example.trippaminebe.domain.account.entity.AccountHistory;
import com.example.trippaminebe.domain.account.entity.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 계좌 거래내역(입출금 한 건) 응답 DTO
@Getter
@Builder
public class AccountHistoryResponse {

  private Long historyId;                   // 계좌내역일련번호
  private TransactionType transactionType;   // 거래유형 (DEPOSIT/WITHDRAW)
  private BigDecimal amount;                 // 거래 금액
  private String description;                // 거래처명
  private LocalDateTime transactionDate;      // 거래 일시
  private BigDecimal balanceAfter;            // 거래 후 잔액

  // Entity -> Response 변환
  public static AccountHistoryResponse from(AccountHistory history) {
    return AccountHistoryResponse.builder()
        .historyId(history.getHistoryId())
        .transactionType(history.getTransactionType())
        .amount(history.getAmount())
        .description(history.getDescription())
        .transactionDate(history.getTransactionDate())
        .balanceAfter(history.getBalanceAfter())
        .build();
  }
}
