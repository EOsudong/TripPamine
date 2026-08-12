package com.example.trippaminebe.domain.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 계좌 별칭 수정 요청 DTO
@Getter
@NoArgsConstructor
public class AccountAliasUpdateRequest {

  @NotBlank(message = "계좌 별칭은 필수입니다.")
  private String accountAlias; // 새로 바꿀 계좌 별칭
}
