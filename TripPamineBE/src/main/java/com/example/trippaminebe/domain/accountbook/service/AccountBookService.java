package com.example.trippaminebe.domain.accountbook.service;

import com.example.trippaminebe.domain.accountbook.repository.AccountBookRepository;
import lombok.RequiredArgsConstructor;
import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountBookService {

   private final AccountBookRepository accountBookRepository;

   public TransactionEntity saveTransaction(TransactionEntity transaction) {
      if (transaction.getTransactionDate() == null) {
         transaction.setTransactionDate(LocalDateTime.now());
      }
      return accountBookRepository.save(transaction);
   }

   public List<TransactionEntity> getTransactions(String username) {
      return accountBookRepository.findByUsernameOrderByTransactionDateDesc(username);
   }

   public TransactionEntity updateTransaction(Long id, TransactionEntity updatedData) {
      TransactionEntity transaction = accountBookRepository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 내역입니다."));

      transaction.setDescription(updatedData.getDescription());
      transaction.setAmount(updatedData.getAmount());
      transaction.setType(updatedData.getType());
      transaction.setCategory(updatedData.getCategory());

      if (updatedData.getTransactionDate() != null) {
         transaction.setTransactionDate(updatedData.getTransactionDate());
      }

      return accountBookRepository.save(transaction);
   }

   public void deleteTransaction(Long id) {
      accountBookRepository.deleteById(id);
   }
}