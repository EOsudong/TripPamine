package com.example.trippaminebe.domain.tour.client;

/** TourAPI 호출/응답 처리 중 발생한 오류를 감싸는 런타임 예외. */
public class TourApiException extends RuntimeException {
  public TourApiException(String message) {
    super(message);
  }

  public TourApiException(String message, Throwable cause) {
    super(message, cause);
  }
}
