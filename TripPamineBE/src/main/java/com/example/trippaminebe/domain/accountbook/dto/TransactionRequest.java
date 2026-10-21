
package com.example.trippaminebe.domain.accountbook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionRequest {

   private String transactionDate;

   @NotBlank(message = "내용을 입력해주세요.")
   @Size(max = 255, message = "내용은 255자 이하여야 합니다.")
   private String description;

   @NotNull(message = "금액을 입력해주세요.")
   @Min(value = 1, message = "금액은 1원 이상 입력해야 합니다.")
   private Long amount;

   @NotBlank(message = "거래 유형을 입력해주세요.")
   @Pattern(
       regexp = "(?i)(income|expense)",
       message = "거래 유형은 income 또는 expense여야 합니다."
   )
   private String type;

   @NotBlank(message = "카테고리를 선택해주세요.")
   @Size(max = 255, message = "카테고리는 255자 이하여야 합니다.")
   private String category;

   @Positive(message = "계좌 ID는 양수여야 합니다.")
   private Long accountId;
}
