package com.example.trippaminebe.domain.accountbook.service;

import com.example.trippaminebe.domain.account.entity.TransactionType;
import com.example.trippaminebe.domain.account.service.AccountBalanceService;
import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import com.example.trippaminebe.domain.user.entity.User;
import com.example.trippaminebe.domain.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Set;
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
   private final ApplicationEventPublisher eventPublisher;

   // NEGO는 내부 AiNegoService가 생성한 결제 기록의 전용 카테고리다.
   // 일반 가계부 API 요청이 같은 카테고리를 임의로 만들어 결제 기록을 위장하지 못하게 한다.
   private static final String NEGO_CATEGORY = "NEGO";

   // 1. 일반 가계부 거래 추가 (외부 API에서 호출)
   public TransactionEntity saveTransaction(Long userId, TransactionEntity transaction) {
      if (isNegoTransaction(transaction)) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "네고 결제 내역은 직접 등록할 수 없습니다.");
      }
      return saveTransactionInternal(userId, transaction);
   }

   // AiNegoService가 사용하는 시스템 전용 거래 등록 진입점.
   // 실제 외부 결제가 아닌 가상 계좌 차감이며, 다른 원장 기록과 같은 DB 트랜잭션에서 처리한다.
   public TransactionEntity saveNegoTransaction(Long userId, TransactionEntity transaction) {
      if (!isNegoTransaction(transaction)) {
         throw new IllegalArgumentException("네고 거래의 카테고리가 올바르지 않습니다.");
      }
      return saveTransactionInternal(userId, transaction);
   }

   private TransactionEntity saveTransactionInternal(Long userId, TransactionEntity transaction) {
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

      // 같은 트랜잭션에서 이벤트를 발행하되 리스너는 커밋 성공 후에만 실행한다.
      publishFinanceChange(userId, saved.getTransactionDate());
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
      // 완료된 네고 가상 결제는 임의 수정/삭제하면 오퍼 전환 상태와 잔액이 불일치한다.
      // 네고 취소·환불은 추후 별도의 검증된 역거래로만 처리한다.
      if (isNegoTransaction(transaction)) {
         throw new ResponseStatusException(HttpStatus.CONFLICT, "네고 결제 내역은 직접 수정하거나 삭제할 수 없습니다.");
      }
      // 일반 거래를 NEGO 카테고리로 변경하는 우회도 차단한다.
      if (isNegoTransaction(updatedData)) {
         throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "네고 결제 카테고리로 변경할 수 없습니다.");
      }

      // 거래일 변경 시 이전 달 요약도 정정해야 하므로 변경 전 날짜를 보존한다.
      LocalDateTime previousDate = transaction.getTransactionDate();
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

      publishFinanceChange(userId, previousDate, saved.getTransactionDate());
      return saved;
   }

   // 4. 거래 내역 삭제
   // 먼저 계좌 잔액 반영을 되돌린 뒤(연동 안 된 항목이면 조용히 넘어감) 가계부 항목 자체를 삭제.
   public void deleteTransaction(Long userId, Long id) {
      requireUserId(userId);
      TransactionEntity transaction = accountBookRepository.findByIdAndUserId(id, userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "본인 가계부 내역을 찾을 수 없습니다."));
      // 완료된 네고 가상 결제는 임의 수정/삭제하면 오퍼 전환 상태와 잔액이 불일치한다.
      // 네고 취소·환불은 추후 별도의 검증된 역거래로만 처리한다.
      if (isNegoTransaction(transaction)) {
         throw new ResponseStatusException(HttpStatus.CONFLICT, "네고 결제 내역은 직접 수정하거나 삭제할 수 없습니다.");
      }
      accountBalanceService.reverseLedgerTransaction(userId, transaction.getId());
      accountBookRepository.delete(transaction);
      publishFinanceChange(userId, transaction.getTransactionDate());
   }

   // 저장·수정·삭제의 대상 월만 전달한다. 실패/롤백된 거래는 리스너가 처리하지 않는다.
   private void publishFinanceChange(Long userId, LocalDateTime... dates) {
      Set<YearMonth> months = new java.util.HashSet<>();
      for (LocalDateTime date : dates) {
         if (date != null) {
            months.add(YearMonth.from(date));
         }
      }
      if (!months.isEmpty()) {
         eventPublisher.publishEvent(new LedgerChangedEvent(userId, months));
      }
   }

   private static boolean isNegoTransaction(TransactionEntity transaction) {
      return transaction != null && NEGO_CATEGORY.equalsIgnoreCase(transaction.getCategory());
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
