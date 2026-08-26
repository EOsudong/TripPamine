package com.example.trippaminebe.domain.finlife.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 예금/적금 상품 하나가 가지는 "기간(개월)별 금리" 옵션 한 줄.
 * 상품 하나(FinancialProductResponse)는 보통 6개월/12개월/24개월/36개월 등 여러 옵션을 가진다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialProductOptionResponse {
  private String saveTrm;          // 저축 기간(개월), 예: "12"
  private String intrRateTypeNm;   // 이자율 종류 이름, 예: "단리", "복리"
  private Double intrRate;         // 기본금리(%)
  private Double intrRate2;        // 최고우대금리(%)
  private String rsrvTypeNm;       // 적립유형 이름, 예: "정액적립식", "자유적립식"
}
