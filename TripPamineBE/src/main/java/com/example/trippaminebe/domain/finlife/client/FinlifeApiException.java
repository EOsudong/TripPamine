package com.example.trippaminebe.domain.finlife.client;

/** 금융감독원 오픈API("금융상품 한눈에", FINLIFE) 호출/응답 처리 중 발생한 오류를 감싸는 런타임 예외. */
public class FinlifeApiException extends RuntimeException {
  public FinlifeApiException(String message) {
    super(message);
  }

  public FinlifeApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
