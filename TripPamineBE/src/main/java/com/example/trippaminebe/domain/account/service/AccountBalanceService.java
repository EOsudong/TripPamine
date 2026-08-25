package com.example.trippaminebe.domain.account.service;

import com.example.trippaminebe.domain.account.entity.Account;
import com.example.trippaminebe.domain.account.entity.AccountHistory;
import com.example.trippaminebe.domain.account.entity.TransactionType;
import com.example.trippaminebe.domain.account.repository.AccountHistoryRepository;
import com.example.trippaminebe.domain.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 가계부(TRANSACTIONS)에서 계좌를 지정해 수입/지출을 입력했을 때, 그 계좌의 잔액에
 * 실제로 반영하는 역할. "계좌연동 → 잔액 표시 → 수입/지출 입력 시 잔액 즉시 반영"
 * 요구사항의 핵심 로직이 여기 다 있다.
 *
 * accountbook 도메인(AccountBookService)이 이 서비스를 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountBalanceService {

  private final AccountRepository accountRepository;
  private final AccountHistoryRepository accountHistoryRepository;

  /**
   * 가계부에 새 항목이 생겼을 때 호출. 계좌 잔액에 반영하고, ACCOUNT_HISTORY에 한 줄 기록한다.
   *
   * @param userId      가계부 항목을 등록한 사용자 (계좌 소유권 검증용)
   * @param accountId   반영할 계좌 (USER_ACCOUNTS.ACCOUNT_ID)
   * @param ledgerTxnId 가계부 항목의 ID (TRANSACTIONS.ID) - 나중에 수정/삭제 시 대응되는 내역을 찾는 키
   */
  public void applyNewLedgerTransaction(Long userId, Long accountId, Long ledgerTxnId,
                                        TransactionType type, BigDecimal amount,
                                        String description, LocalDateTime date) {
    Account account = getOwnedAccountOrThrow(userId, accountId);

    BigDecimal signedDelta = (type == TransactionType.DEPOSIT) ? amount : amount.negate();
    account.adjustBalance(signedDelta);

    AccountHistory history = AccountHistory.builder()
        .account(account)
        .transactionType(type)
        .amount(amount)
        .description(description)
        .balanceAfter(account.getBalance())
        .transactionDate(date)
        .ledgerTxnId(ledgerTxnId)
        .build();
    accountHistoryRepository.save(history);

    log.info("[AccountBalance] accountId={} 잔액 반영: {}{} → 잔액 {}",
        accountId, type == TransactionType.DEPOSIT ? "+" : "-", amount, account.getBalance());
  }

  /**
   * 가계부 항목이 삭제되거나, 수정으로 인해 기존 반영을 무효화해야 할 때 호출.
   * 대응되는 ACCOUNT_HISTORY가 없으면(원래 계좌 연동 없이 등록된 항목이었다면) 조용히 넘어간다.
   */
  public void reverseLedgerTransaction(Long ledgerTxnId) {
    accountHistoryRepository.findByLedgerTxnId(ledgerTxnId).ifPresent(history -> {
      Account account = history.getAccount();
      BigDecimal signedDelta = (history.getTransactionType() == TransactionType.DEPOSIT)
          ? history.getAmount().negate()
          : history.getAmount();
      account.adjustBalance(signedDelta);
      accountHistoryRepository.delete(history);

      log.info("[AccountBalance] accountId={} 잔액 반영 취소: 잔액 {}",
          account.getAccountId(), account.getBalance());
    });
  }

  /**
   * 가계부 항목이 수정됐을 때 호출. 기존 반영을 전부 되돌린 뒤, 계좌가 지정돼 있으면
   * 새 내용으로 다시 반영한다 (계좌가 바뀌었거나, 아예 계좌 연동을 해제한 경우까지 함께 처리됨).
   */
  public void updateLedgerTransaction(Long userId, Long ledgerTxnId, Long newAccountId,
                                      TransactionType newType, BigDecimal newAmount,
                                      String newDescription, LocalDateTime newDate) {
    reverseLedgerTransaction(ledgerTxnId);
    if (newAccountId != null) {
      applyNewLedgerTransaction(userId, newAccountId, ledgerTxnId, newType, newAmount, newDescription, newDate);
    }
  }

  private Account getOwnedAccountOrThrow(Long userId, Long accountId) {
    return accountRepository.findByAccountIdAndUser_Id(accountId, userId)
        .orElseThrow(() -> new IllegalArgumentException(
            "본인 소유의 계좌가 아니거나 존재하지 않습니다. accountId=" + accountId));
  }
}
