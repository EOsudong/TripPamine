package com.example.trippaminebe.domain.finlife.controller;

import com.example.trippaminebe.domain.finlife.dto.FinancialProductResponse;
import com.example.trippaminebe.domain.finlife.dto.FinancialRecommendationResponse;
import com.example.trippaminebe.domain.finlife.service.FinancialProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 금융감독원 오픈API(금융상품 한눈에, FINLIFE) 데이터를 프론트 사이드바 "정보" 그룹의
 * "금융상품" 메뉴에 내려주는 컨트롤러.
 */
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinancialProductController {

  private final FinancialProductService financialProductService;

  @GetMapping("/deposits")
  @Operation(summary = "정기예금 상품 목록 조회 (금융감독원 금융상품 한눈에 API)")
  public ResponseEntity<List<FinancialProductResponse>> getDeposits(
      @RequestParam(defaultValue = "bank")
      @Parameter(description = "bank(시중은행, 기본값) | savings(저축은행) | all(전체)") String bankType
  ) {
    return ResponseEntity.ok(financialProductService.getDeposits(bankType));
  }

  @GetMapping("/savings")
  @Operation(summary = "적금 상품 목록 조회 (금융감독원 금융상품 한눈에 API)")
  public ResponseEntity<List<FinancialProductResponse>> getSavings(
      @RequestParam(defaultValue = "bank")
      @Parameter(description = "bank(시중은행, 기본값) | savings(저축은행) | all(전체)") String bankType
  ) {
    return ResponseEntity.ok(financialProductService.getSavings(bankType));
  }

  @GetMapping("/recommendations")
  @Operation(summary = "예치금액/기간 조건에 맞는 금융상품 추천 (규칙 기반, 외부 AI 미사용)")
  public ResponseEntity<List<FinancialRecommendationResponse>> getRecommendations(
      @RequestParam(defaultValue = "deposit")
      @Parameter(description = "deposit(정기예금, 기본값) | saving(적금)") String productType,
      @RequestParam(defaultValue = "bank")
      @Parameter(description = "bank(시중은행, 기본값) | savings(저축은행) | all(전체)") String bankType,
      @RequestParam(required = false)
      @Parameter(description = "예치/적립 기간(개월). 생략 시 12개월 기준") Integer months,
      @RequestParam(required = false)
      @Parameter(description = "예치금액(원). 있으면 세전 예상이자를 함께 계산해줌") Long amount,
      @RequestParam(required = false, defaultValue = "5")
      @Parameter(description = "추천 개수 (기본 5, 최대 20)") Integer limit
  ) {
    return ResponseEntity.ok(
        financialProductService.recommend(productType, bankType, months, amount, limit));
  }
}
