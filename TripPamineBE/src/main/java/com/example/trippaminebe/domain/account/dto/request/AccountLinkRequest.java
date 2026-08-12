package com.example.trippaminebe.domain.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 계좌 연동 등록 요청 DTO
@Getter
@NoArgsConstructor
public class AccountLinkRequest {

  @NotBlank(message = "은행명은 필수입니다.")
  private String bankName; // 은행/기관명 (예: "신한은행", "카카오페이")

  private String bankCode; // 은행/기관 코드 (선택 입력, 금융결제원 표준코드 등)

  @NotBlank(message = "계좌번호는 필수입니다.")
  private String accountNumber; // 계좌번호 (서버에서 암호화되어 저장됨)

  private String fintechUseNum; // 오픈뱅킹 핀테크 이용번호 (선택 입력)

  private String accountAlias; // 계좌별칭 (선택 입력, 사용자가 원하는 이름)
}
