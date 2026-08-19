package com.example.trippaminebe.domain.accountbook.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TransactionRequest {
   private String transactionDate;

   @NotBlank(message = "내용을 입력해주세요.")
   private String description;

   @NotNull(message = "금액을 입력해주세요.")
   @Min(value = 1, message = "금액은 1원 이상 입력해야 합니다.")
   private Long amount;

   private String type;     // "income" | "expense"
   private String category;
}