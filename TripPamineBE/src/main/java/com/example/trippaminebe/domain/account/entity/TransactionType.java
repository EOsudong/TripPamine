package com.example.trippaminebe.domain.account.entity;

/**
 * ACCOUNT_HISTORY.TRANSACTION_TYPE 매핑용 Enum
 * DB CHECK 제약(CK_HISTORY_TYPE)과 동일한 값: DEPOSIT / WITHDRAW
 * Enum 이름과 DB 저장값이 동일하므로 EnumType.STRING 으로 바로 매핑 가능
 */
public enum TransactionType {
  DEPOSIT,  // 입금
  WITHDRAW  // 출금
}
