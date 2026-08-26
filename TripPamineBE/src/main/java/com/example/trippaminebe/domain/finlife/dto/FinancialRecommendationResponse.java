package com.example.trippaminebe.domain.finlife.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * "예치금액/기간 조건에 맞는 금융상품 추천" (규칙 기반, 외부 AI 미사용) 결과 한 건.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialRecommendationResponse {
  private String matchedTerm;        // 추천 근거로 사용한 기간(개월), 예: "12"
  private Double matchedIntrRate;    // 위 기간의 기본금리(%)
  private Double matchedIntrRate2;   // 위 기간의 최고우대금리(%)
  private Double estimatedInterest;  // 입력한 예치금액 기준 세전 예상이자(단리 계산, 참고용). 금액 미입력 시 null
  private String reason;             // 추천 이유 한 줄 설명
  private FinancialProductResponse product; // 상품 상세(옵션 전체 포함)
}
