package com.example.trippaminebe.domain.accountbook.repository;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;


public interface AccountBookRepository
        extends JpaRepository<TransactionEntity, Long> {


   // =========================================
   // 기존 기능
   // 특정 사용자의 전체 가계부 내역
   // 최신순 조회
   // =========================================

   List<TransactionEntity>
   findByUsernameOrderByTransactionDateDesc(
           String username
   );


   // =========================================
   // 자산관리 Dashboard용
   //
   // 특정 사용자의 특정 기간 거래 조회
   //
   // 예:
   // 2026-09-01 00:00
   // ~
   // 2026-10-01 00:00 미만
   // =========================================

   List<TransactionEntity>
   findByUsernameAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
           String username,
           LocalDateTime startDate,
           LocalDateTime endDate
   );

}