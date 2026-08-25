package com.example.trippaminebe.domain.account.repository;

import com.example.trippaminebe.domain.account.entity.AccountHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountHistoryRepository extends JpaRepository<AccountHistory, Long> {

  // 계좌 하나의 거래내역을 최신순 페이징 조회
  Page<AccountHistory> findByAccount_AccountIdOrderByTransactionDateDesc(Long accountId, Pageable pageable);

  // [Mock 은행 연동 추가] 가계부 항목(TRANSACTIONS.ID) 하나에 대응하는 계좌내역을 찾음.
  // 가계부 항목을 수정/삭제할 때 이전 잔액 반영을 되돌리기 위해 사용 (AccountBalanceService).
  Optional<AccountHistory> findByLedgerTxnId(Long ledgerTxnId);
}
