
package com.example.trippaminebe.domain.accountbook.dto;

import com.example.trippaminebe.domain.accountbook.entity.TransactionEntity;
import lombok.Getter;

@Getter
public class TransactionResponse {

   private Long id;
   private String transactionDate;
   private String description;
   private Long amount;
   private String type;
   private String category;
   private Long accountId;

   public TransactionResponse(TransactionEntity entity) {
      this.id = entity.getId();

      if (entity.getTransactionDate() != null) {
         this.transactionDate = entity.getTransactionDate().toString();
      }

      this.description = entity.getDescription();
      this.amount = entity.getAmount();
      this.type = entity.getType();
      this.category = entity.getCategory();
      this.accountId = entity.getAccountId();
   }
}