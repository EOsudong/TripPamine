package com.example.trippaminebe.domain.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 계좌 연동 등록 요청 DTO
// [Mock 은행 연동 추가] @Setter를 추가함 - @Getter만 있으면 Jackson이 기본 생성자로 만든
// 객체에 값을 채울 방법이 없어 @RequestBody 역직렬화가 실패할 수 있다 (다른 요청 DTO인
// TransactionRequest도 같은 이유로 이미 @Setter를 쓰고 있음).
@Getter
@Setter
@NoArgsConstructor
public class AccountLinkRequest {

  @NotBlank(message = "은행명은 필수입니다.")
  private String bankName; // 은행/기관명 (예: "신한은행", "카카오페이")

  private String bankCode; // 은행/기관 코드 (선택 입력, 금융결제원 표준코드 등)

  @NotBlank(message = "계좌번호는 필수입니다.")
  private String accountNumber; // 계좌번호 (서버에서 암호화되어 저장됨)

  // [Mock 은행 연동 추가] 더 이상 사용자가 직접 입력하지 않음 - AccountService.linkAccount()가
  // Mock 은행 서버(MockOpenBankingClient.verifyAccount)의 계좌 실명확인 결과로 서버에서 발급/할당한다.
  // 과거 클라이언트가 이 필드를 채워 보내더라도 서버는 무시한다.
  @Deprecated
  private String fintechUseNum;

  private String accountAlias; // 계좌별칭 (선택 입력, 사용자가 원하는 이름)
}
