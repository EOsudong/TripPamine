package com.example.trippaminebe.domain.finlife.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 금융상품 비교 화면의 세전 이자 참고 계산기.
 *
 * 예금은 가입일 일시예치, 적금은 매월 초 동일 금액 납입을 가정한다.
 * FINLIFE 공시의 단리/복리 정보만 사용하며, 복리는 월복리로 가정한다.
 * 실제 상품의 일수 계산, 우대조건, 납입 지연, 세금 등은 반영하지 않는다.
 */
final class FinancialInterestEstimateCalculator {

  private static final MathContext CALCULATION_PRECISION = new MathContext(24, RoundingMode.HALF_UP);
  private static final BigDecimal MONTHLY_RATE_DIVISOR = BigDecimal.valueOf(1200);

  private FinancialInterestEstimateCalculator() {
  }

  static Double estimate(
      String productType, String interestType, int months, Long amount, Double annualRate
  ) {
    if (amount == null || amount <= 0 || months <= 0 || months > 120
        || annualRate == null || !Double.isFinite(annualRate) || annualRate < 0
        || interestType == null) {
      return null;
    }

    // 원본 공시가 단리/복리를 알려주지 않으면 잘못된 가정으로 이자를 산출하지 않는다.
    boolean simple = interestType.contains("단리");
    boolean compound = interestType.contains("복리");
    if (simple == compound) {
      return null;
    }

    BigDecimal monthlyRate = BigDecimal.valueOf(annualRate)
        .divide(MONTHLY_RATE_DIVISOR, CALCULATION_PRECISION);
    BigDecimal principal = BigDecimal.valueOf(amount);
    BigDecimal interest;

    if ("saving".equals(productType)) {
      if (simple) {
        // 적금: 첫 납입액은 m개월, 마지막 납입액은 1개월치 이자를 얻는다.
        BigDecimal interestMonths = BigDecimal.valueOf(months)
            .multiply(BigDecimal.valueOf(months + 1L))
            .divide(BigDecimal.valueOf(2), CALCULATION_PRECISION);
        interest = principal.multiply(monthlyRate, CALCULATION_PRECISION)
            .multiply(interestMonths, CALCULATION_PRECISION);
      } else {
        // 복리 적금: 매월 납입액의 남은 운용 개월 수가 각기 다르다.
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate);
        interest = BigDecimal.ZERO;
        for (int remainingMonths = 1; remainingMonths <= months; remainingMonths++) {
          interest = interest.add(principal.multiply(
              growth.pow(remainingMonths, CALCULATION_PRECISION).subtract(BigDecimal.ONE),
              CALCULATION_PRECISION), CALCULATION_PRECISION);
        }
      }
    } else if ("deposit".equals(productType)) {
      if (simple) {
        interest = principal.multiply(monthlyRate, CALCULATION_PRECISION)
            .multiply(BigDecimal.valueOf(months), CALCULATION_PRECISION);
      } else {
        interest = principal.multiply(
            BigDecimal.ONE.add(monthlyRate).pow(months, CALCULATION_PRECISION)
                .subtract(BigDecimal.ONE), CALCULATION_PRECISION);
      }
    } else {
      return null;
    }

    // 화면의 '원' 단위 참고 금액. 실제 금융기관의 원 단위 절사 정책과 다를 수 있다.
    return interest.setScale(0, RoundingMode.HALF_UP).doubleValue();
  }

  static String assumption(String productType, String interestType) {
    if (interestType == null || interestType.contains("단리") == interestType.contains("복리")) {
      return "이자 계산 방식이 확인되지 않아 예상이자는 표시하지 않습니다.";
    }
    String rateBasis = interestType.contains("복리") ? "월복리 가정" : "단리 가정";
    return "saving".equals(productType)
        ? "매월 초 동일 금액 납입 · " + rateBasis + " · 세전 참고"
        : "가입일 일시예치 · " + rateBasis + " · 세전 참고";
  }
}
