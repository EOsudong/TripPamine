package com.example.trippaminebe.domain.finlife.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 금융감독원 오픈API(금융상품 한눈에) 정기예금/적금 원본 응답(baseList + optionList)을
 * 화면에서 바로 쓰기 좋은 형태로 합쳐서 정리한 DTO.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialProductResponse {
  private String finCoNo;              // 금융회사 코드
  private String korCoNm;              // 금융회사명 (예: "국민은행")
  private String finPrdtCd;            // 금융상품 코드
  private String finPrdtNm;            // 금융상품명
  private String productType;          // "deposit"(정기예금) | "saving"(적금)
  private String joinWay;              // 가입방법
  private String joinMember;           // 가입대상
  private String joinDenyLabel;        // 가입제한 (제한없음 | 서민전용 | 일부제한)
  private String spclCnd;              // 우대조건
  private String mtrtInt;              // 만기 후 이자율 설명
  private String etcNote;              // 기타 유의사항
  private String dclsMonth;            // 공시 제출월 (yyyyMM)
  private Double maxRate;              // options 중 최고우대금리(intrRate2)의 최댓값 - 목록 정렬/카드 대표 금리 표시용
  private List<FinancialProductOptionResponse> options; // 기간(개월)별 금리 목록
}
