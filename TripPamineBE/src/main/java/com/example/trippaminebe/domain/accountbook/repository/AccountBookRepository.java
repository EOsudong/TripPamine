package com.example.trippaminebe.domain.accountbook.repository;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AccountBookRepository extends JpaRepository<TransactionEntity, Long> {
   // 특정 유저의 가계부 내역만 최신순으로 조회
   List<TransactionEntity> findByUsernameOrderByTransactionDateDesc(String username);
}