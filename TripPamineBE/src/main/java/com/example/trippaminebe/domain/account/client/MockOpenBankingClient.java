package com.example.trippaminebe.domain.account.client;

import com.example.trippaminebe.domain.mockbank.dto.MockAccountVerifyRequest;
import com.example.trippaminebe.domain.mockbank.dto.MockAccountVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 계좌 연동 시점에 딱 1번 "실명확인"만 호출하면 되고(계좌 연동 이후 잔액은 우리
 * 서비스 안에서만 바뀌므로), 별도의 잔액조회/거래내역조회 API 호출은 필요 없다.
 */
@Slf4j
@Component
public class MockOpenBankingClient {

  private final RestTemplate restTemplate;

  // 기본값은 같은 서버(localhost) - Mock 서버가 이 애플리케이션 안에 함께 떠 있기 때문.
  // 실제 오픈뱅킹으로 교체 시 application.yaml의 mock-bank.base-url만 실제 API 주소로 바꾸면 됨.
  @Value("${mock-bank.base-url:http://localhost:${server.port:8080}}")
  private String baseUrl;

  public MockOpenBankingClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    this.restTemplate = new RestTemplate(factory);
  }

  // 계좌 실명확인 + 핀테크이용번호/최초 잔액 발급
  public MockAccountVerifyResponse verifyAccount(String bankCode, String bankName,
                                                 String accountNumber, String accountHolderName) {
    MockAccountVerifyRequest request = new MockAccountVerifyRequest(bankCode, bankName, accountNumber, accountHolderName);
    try {
      return restTemplate.postForObject(baseUrl + "/mock-bank/accounts/verify", request, MockAccountVerifyResponse.class);
    } catch (Exception e) {
      log.error("[MockOpenBankingClient] 계좌 실명확인 호출 실패: bank={}, accountNumber=****", bankName, e);
      throw new IllegalStateException("은행 서버와 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
    }
  }
}
