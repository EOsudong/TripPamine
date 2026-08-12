package com.example.trippaminebe.domain.account.repository;

import com.example.trippaminebe.domain.account.entity.AccountHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHistoryRepository extends JpaRepository<AccountHistory, Long> {

  // 계좌 하나의 거래내역을 최신순 페이징 조회
  Page<AccountHistory> findByAccount_AccountIdOrderByTransactionDateDesc(Long accountId, Pageable pageable);
}
