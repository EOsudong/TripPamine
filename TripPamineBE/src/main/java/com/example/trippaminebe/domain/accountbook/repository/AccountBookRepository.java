
package com.example.trippaminebe.domain.accountbook.repository;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountBookRepository
    extends JpaRepository<TransactionEntity, Long> {

   // 거래 ID와 사용자 ID를 함께 확인
   Optional<TransactionEntity> findByIdAndUserId(Long id, Long userId);

   // 로그인 사용자의 전체 거래
   List<TransactionEntity> findByUserIdOrderByTransactionDateDesc(Long userId);

   // 특정 기간 거래 조회
   List<TransactionEntity> findByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
       Long userId,
       LocalDateTime startDate,
       LocalDateTime endDate
   );

   // 특정 날짜 이후 거래
   List<TransactionEntity> findByUserIdAndTransactionDateGreaterThanEqual(
       Long userId,
       LocalDateTime startDate
   );

   // 지정 기간 거래 존재 여부
   boolean existsByUserIdAndTransactionDateGreaterThanEqualAndTransactionDateLessThan(
       Long userId,
       LocalDateTime startDate,
       LocalDateTime endDate
   );
}
