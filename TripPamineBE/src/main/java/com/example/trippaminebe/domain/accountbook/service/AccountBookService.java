package com.example.trippaminebe.domain.accountbook.service;

import com.example.trippaminebe.domain.account.entity.TransactionType;
import com.example.trippaminebe.domain.account.service.AccountBalanceService;
import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [Mock 은행 연동 추가]
 * 가계부 항목(TransactionEntity)에 accountId가 지정돼 있으면, 저장/수정/삭제할 때마다
 * AccountBalanceService를 함께 호출해서 계좌 잔액(USER_ACCOUNTS.BALANCE)과 계좌
 * 거래내역(ACCOUNT_HISTORY)에 실시간으로 반영한다.
 * - "계좌연동 → 잔액 표시 → 수입/지출 입력 시 잔액 즉시 반영"
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AccountBookService {

   private final AccountBookRepository accountBookRepository;
   private final UserRepository userRepository;
   private final AccountBalanceService accountBalanceService;

   // 1. 거래 내역 추가
   public TransactionEntity saveTransaction(Long userId, TransactionEntity transaction) {
      requireUserId(userId);
      User user = userRepository.findById(userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

      // 요청 본문의 id, username, userId는 신뢰하지 않는다.
      transaction.setId(null);
      transaction.setUserId(user.getId());
      if (transaction.getTransactionDate() == null) {
         transaction.setTransactionDate(LocalDateTime.now());
      }

      TransactionEntity saved = accountBookRepository.save(transaction);

      if (saved.getAccountId() != null) {
         accountBalanceService.applyNewLedgerTransaction(
             userId,
             saved.getAccountId(),
             saved.getId(),
             toTransactionType(saved.getType()),
             BigDecimal.valueOf(saved.getAmount()),
             saved.getDescription(),
             saved.getTransactionDate()
         );
      }

      return saved;
   }

   @Transactional(readOnly = true)
   public List<TransactionEntity> getTransactions(Long userId) {
      requireUserId(userId);
      return accountBookRepository.findByUserIdOrderByTransactionDateDesc(userId);
   }

   // 3. 거래 내역 수정
   // accountId가 바뀌었거나, 계좌 연동을 새로 걸거나 해제한 경우까지 전부
   // AccountBalanceService.updateLedgerTransaction() 한 번으로 처리된다
   public TransactionEntity updateTransaction(Long userId, Long id, TransactionEntity updatedData) {
      requireUserId(userId);
      TransactionEntity transaction = accountBookRepository.findByIdAndUserId(id, userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "본인 가계부 내역을 찾을 수 없습니다."));

      transaction.setDescription(updatedData.getDescription());
      transaction.setAmount(updatedData.getAmount());
      transaction.setType(updatedData.getType());
      transaction.setCategory(updatedData.getCategory());
      transaction.setAccountId(updatedData.getAccountId());

      if (updatedData.getTransactionDate() != null) {
         transaction.setTransactionDate(updatedData.getTransactionDate());
      }

      TransactionEntity saved = accountBookRepository.save(transaction);

      accountBalanceService.updateLedgerTransaction(
          userId,
          saved.getId(),
          saved.getAccountId(),
          toTransactionType(saved.getType()),
          BigDecimal.valueOf(saved.getAmount()),
          saved.getDescription(),
          saved.getTransactionDate()
      );

      return saved;
   }

   // 4. 거래 내역 삭제
   // 먼저 계좌 잔액 반영을 되돌린 뒤(연동 안 된 항목이면 조용히 넘어감) 가계부 항목 자체를 삭제.
   public void deleteTransaction(Long userId, Long id) {
      requireUserId(userId);
      TransactionEntity transaction = accountBookRepository.findByIdAndUserId(id, userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "본인 가계부 내역을 찾을 수 없습니다."));
      accountBalanceService.reverseLedgerTransaction(userId, transaction.getId());
      accountBookRepository.delete(transaction);
   }

   private static void requireUserId(Long userId) {
      if (userId == null || userId <= 0) {
         throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
      }
   }

   // "income" -> DEPOSIT(입금), 그 외("expense" 등) -> WITHDRAW(출금)
   private TransactionType toTransactionType(String type) {
      return "income".equalsIgnoreCase(type) ? TransactionType.DEPOSIT : TransactionType.WITHDRAW;
   }
}
