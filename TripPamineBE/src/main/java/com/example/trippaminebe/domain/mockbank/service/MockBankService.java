package com.example.trippaminebe.domain.mockbank.service;

import com.example.trippaminebe.domain.mockbank.dto.MockAccountVerifyRequest;
import com.example.trippaminebe.domain.mockbank.dto.MockAccountVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * "Mock 은행 서버"의 비즈니스 로직.
 *
 *   1) 계좌를 연동하면 그 계좌의 잔액을 한 번 받아와서 USER_ACCOUNTS.BALANCE에 저장하고
 *   2) 이후 잔액 변화는 전부 우리 서비스 안(가계부에 수입/지출 입력)에서만 일어난다.
 */
@Slf4j
@Service
public class MockBankService {

  // 계좌 하나당 발급할 잔액의 범위 (계좌번호가 같으면 항상 같은 값이 나옴)
  private static final long BALANCE_MIN = 50_000L;
  private static final long BALANCE_RANGE = 4_950_000L; // BALANCE_MIN ~ 약 500만원 사이

  public MockAccountVerifyResponse verifyAccount(MockAccountVerifyRequest request) {
    // bankCode는 선택 입력이라 비어있을 수 있어, 없으면 은행명으로 대체해서 시드를 구성한다.
    String normalizedBankCode = normalizeBankCode(request.getBankCode(), request.getBankName());
    String seed = normalizedBankCode + ":" + request.getAccountNumber();

    BigDecimal balance = deterministicBalance(seed);
    String fintechUseNum = deterministicFintechUseNum(seed);
    String holderName = (request.getAccountHolderName() == null || request.getAccountHolderName().isBlank())
        ? "예금주" + tail(request.getAccountNumber(), 4)
        : request.getAccountHolderName();

    log.info("[MockBank] 계좌 실명확인 완료: bank={}, fintechUseNum={}, balance={}",
        request.getBankName(), fintechUseNum, balance);

    return MockAccountVerifyResponse.builder()
        .fintechUseNum(fintechUseNum)
        .bankCode(normalizedBankCode)
        .bankName(request.getBankName())
        .accountHolderName(holderName)
        .balance(balance)
        .build();
  }

  // 계좌번호(등 시드 문자열)를 기반으로 항상 같은 값이 나오는 "고정" 잔액을 계산.
  // String.hashCode()는 JVM을 껐다 켜도 문자열 내용이 같으면 항상 같은 값이 나오는 것이
  // 자바 명세에 보장되어 있어(재실행해도 값이 안 바뀜), 별도 DB 저장 없이도 "같은 계좌번호로
  // 연동하면 항상 같은 잔액"을 만들어낼 수 있다.
  private BigDecimal deterministicBalance(String seed) {
    long hash = Math.abs((long) seed.hashCode());
    long balance = BALANCE_MIN + (hash % BALANCE_RANGE);
    balance = (balance / 1000) * 1000; // 천원 단위로 보기 좋게 정리
    return BigDecimal.valueOf(balance);
  }

  private String deterministicFintechUseNum(String seed) {
    // 실제 오픈뱅킹 핀테크이용번호 포맷을 흉내낸, seed로부터 결정론적으로 나오는 문자열
    return "MOCK" + Integer.toHexString(seed.hashCode()).toUpperCase()
        + Integer.toHexString(seed.length() * 31).toUpperCase();
  }

  private String normalizeBankCode(String bankCode, String bankName) {
    if (bankCode != null && !bankCode.isBlank()) {
      return bankCode;
    }
    return "BANKNAME:" + bankName;
  }

  private String tail(String value, int length) {
    if (value == null) return "";
    return value.length() <= length ? value : value.substring(value.length() - length);
  }
}
