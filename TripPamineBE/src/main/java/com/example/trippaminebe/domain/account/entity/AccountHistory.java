package com.example.trippaminebe.domain.account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ACCOUNT_HISTORY 테이블 매핑
 * 사용자 계좌 입출금 상세 내역
 *
 * [Mock 은행 연동 추가] ledgerTxnId 컬럼을 새로 추가했다. 가계부(TRANSACTIONS)에서
 * "이 계좌로" 지정해서 수입/지출을 입력하면, 그 내용이 이 테이블에도 한 줄 그대로
 * 기록되고 ledgerTxnId에 그 가계부 항목(TRANSACTIONS.ID)을 저장해둔다. 나중에 그
 * 가계부 항목을 수정/삭제할 때 이 값으로 대응되는 계좌내역을 찾아서 잔액 반영을
 * 되돌리거나 다시 계산한다
 */
@Entity
@Table(name = "ACCOUNT_HISTORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountHistory {

  @Id
  @Column(name = "HISTORY_ID")
  @SequenceGenerator(
      name = "seqAccountHistory",
      sequenceName = "SEQ_ACCOUNT_HISTORY",
      allocationSize = 1
  )
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seqAccountHistory")
  private Long historyId; // 계좌내역일련번호: 내부 고유 식별자 (Sequence 적용) PK

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ACCOUNT_ID", nullable = false)
  private Account account; // 계좌연동일련번호: USER_ACCOUNTS(ACCOUNT_ID) 참조 FK - 이 거래가 발생한 계좌

  @Enumerated(EnumType.STRING)
  @Column(name = "TRANSACTION_TYPE", length = 10, nullable = false)
  private TransactionType transactionType; // 거래유형 - DEPOSIT(입금) 또는 WITHDRAW(출금)

  @Column(name = "AMOUNT", precision = 20, nullable = false)
  private BigDecimal amount; // 거래 금액

  @Column(name = "DESCRIPTION", length = 50)
  private String description; // 거래처명 (예: "스타벅스 신촌점", "카카오페이 충전")

  @Column(name = "TRANSACTION_DATE")
  private LocalDateTime transactionDate; // 거래 일시

  @Column(name = "BALANCE_AFTER", precision = 20, nullable = false)
  private BigDecimal balanceAfter; // 거래 후 잔액

  // 이 내역을 발생시킨 가계부 항목의 ID (TRANSACTIONS.ID). 가계부와 연동되지 않은 채
  // 과거 방식으로 등록된 내역이 있다면 null일 수 있어 nullable로 둔다.
  @Column(name = "LEDGER_TXN_ID")
  private Long ledgerTxnId;

  @Builder
  private AccountHistory(Account account, TransactionType transactionType,
                         BigDecimal amount, String description, BigDecimal balanceAfter,
                         Long ledgerTxnId, LocalDateTime transactionDate) {
    this.account = account;
    this.transactionType = transactionType;
    this.amount = amount;
    this.description = description;
    this.balanceAfter = balanceAfter;
    this.ledgerTxnId = ledgerTxnId;
    this.transactionDate = transactionDate;
  }

  // 저장 직전 자동 실행: 거래일시를 안 넣고 만들어도 현재 시각으로 채워줌
  @PrePersist
  private void prePersist() {
    if (this.transactionDate == null) {
      this.transactionDate = LocalDateTime.now();
    }
  }
}
