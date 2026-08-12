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
 * 주의: DDL의 COMMENT에는 TRANSACTION_NUM(더미 API 거래 고유 승인번호) 컬럼 설명이
 * 있지만, 실제 CREATE TABLE 구문에는 해당 컬럼이 존재하지 않음 (스키마 불일치).
 * DB 담당자/팀 확인 후 컬럼이 추가되면 아래에 필드를 함께 추가할 것.
 * ex) @Column(name = "TRANSACTION_NUM", length = 50) private String transactionNum;
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

  // TODO: 스키마에 TRANSACTION_NUM 컬럼 추가되면 여기에 필드 매핑

  @Builder
  private AccountHistory(Account account, TransactionType transactionType,
                         BigDecimal amount, String description, BigDecimal balanceAfter) {
    this.account = account;
    this.transactionType = transactionType;
    this.amount = amount;
    this.description = description;
    this.balanceAfter = balanceAfter;
  }

  // 저장 직전 자동 실행: 거래일시를 안 넣고 만들어도 현재 시각으로 채워줌
  @PrePersist
  private void prePersist() {
    if (this.transactionDate == null) {
      this.transactionDate = LocalDateTime.now();
    }
  }
}
