package com.example.trippaminebe.domain.accountbook.dto;

import lombok.Getter;
import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;

@Getter
public class TransactionResponse {
   private Long id;
   private String username;
   private String transactionDate;
   private String description;
   private Long amount;
   private String type;
   private String category;

   public TransactionResponse(TransactionEntity entity) {
      this.id = entity.getId();
      this.username = entity.getUsername();
      if (entity.getTransactionDate() != null) {
         this.transactionDate = entity.getTransactionDate().toString();
      }
      this.description = entity.getDescription();
      this.amount = entity.getAmount();
      this.type = entity.getType();
      this.category = entity.getCategory();
   }
}