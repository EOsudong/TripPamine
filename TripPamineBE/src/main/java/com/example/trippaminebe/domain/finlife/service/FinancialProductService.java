package com.example.trippaminebe.domain.finlife.service;

import com.example.trippaminebe.domain.finlife.dto.FinancialProductOptionResponse;
import com.example.trippaminebe.domain.finlife.dto.FinancialProductResponse;
import com.example.trippaminebe.domain.finlife.dto.FinancialRecommendationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 컨트롤러가 직접 의존하는 서비스. 사용자 요청 경로에서는 FINLIFE API를 절대 직접 호출하지 않고,
 * FinlifeCacheService가 들고 있는 DB 캐시(FINLIFE_CACHE_ENTRY)만 읽는다.
 */
@Service
@RequiredArgsConstructor
public class FinancialProductService {

  private static final int DEFAULT_RECOMMEND_MONTHS = 12;
  private static final int DEFAULT_RECOMMEND_LIMIT = 5;
  private static final int MAX_RECOMMEND_LIMIT = 20;

  private final FinlifeCacheService finlifeCacheService;

  /** 정기예금 목록. bankType: "bank"(시중은행, 기본값) | "savings"(저축은행) | "all"(둘 다) */
  public List<FinancialProductResponse> getDeposits(String bankType) {
    return getProducts("deposit", bankType);
  }

  /** 적금 목록. bankType: "bank"(시중은행, 기본값) | "savings"(저축은행) | "all"(둘 다) */
  public List<FinancialProductResponse> getSavings(String bankType) {
    return getProducts("saving", bankType);
  }

  /**
   * 예치금액/기간 조건에 맞는 금융상품 추천 (규칙 기반, 외부 AI 호출 없음).
   */
  public List<FinancialRecommendationResponse> recommend(
      String productType, String bankType, Integer months, Long amount, Integer limit
  ) {
    int normalizedMonths = (months == null || months <= 0) ? DEFAULT_RECOMMEND_MONTHS : months;
    int normalizedLimit = Math.max(1, Math.min(limit == null ? DEFAULT_RECOMMEND_LIMIT : limit, MAX_RECOMMEND_LIMIT));

    List<FinancialProductResponse> candidates = "saving".equalsIgnoreCase(productType)
        ? getSavings(bankType)
        : getDeposits(bankType);

    // 요청한 가입기간과 정확히 일치하는 상품만 비교한다. 다른 만기를 임의로 대체하지 않는다.
    return candidates.stream()
        .map(product -> toRecommendation(product, normalizedMonths, amount))
        .filter(rec -> rec != null)
        .sorted(Comparator.comparing(
                FinancialRecommendationResponse::getMatchedIntrRate,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(FinancialRecommendationResponse::getMatchedIntrRate2,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(normalizedLimit)
        .collect(Collectors.toList());
  }

  private FinancialRecommendationResponse toRecommendation(
      FinancialProductResponse product, int months, Long amount
  ) {
    if (product.getOptions() == null || product.getOptions().isEmpty()) {
      return null;
    }

    FinancialProductOptionResponse matched = pickExactOption(product.getOptions(), months);
    if (matched == null) {
      return null;
    }

    String productType = product.getProductType();
    String interestType = matched.getIntrRateTypeNm();
    Double estimatedBaseInterest = FinancialInterestEstimateCalculator.estimate(
        productType, interestType, months, amount, matched.getIntrRate());
    Double estimatedMaxInterest = FinancialInterestEstimateCalculator.estimate(
        productType, interestType, months, amount, matched.getIntrRate2());

    String reason = months + "개월 가입 조건 · 기본금리 우선 비교 (우대조건 충족 여부는 미확인)";
    return FinancialRecommendationResponse.builder()
        .matchedTerm(matched.getSaveTrm())
        .matchedIntrRate(matched.getIntrRate())
        .matchedIntrRate2(matched.getIntrRate2())
        .estimatedInterest(estimatedBaseInterest)
        .estimatedMaxInterest(estimatedMaxInterest)
        .estimateBasis(FinancialInterestEstimateCalculator.assumption(productType, interestType))
        .reason(reason)
        .product(product)
        .build();
  }

  // 공시 옵션에 요청한 개월 수가 없으면 추천 대상에서 제외한다.
  // 같은 기간에 여러 옵션(적립유형/이자방식)이 있으면 기본금리가 높은 옵션을 선택한다.
  private FinancialProductOptionResponse pickExactOption(
      List<FinancialProductOptionResponse> options, int months
  ) {
    return options.stream()
        .filter(option -> option != null && option.getSaveTrm() != null)
        .filter(option -> parseMonthsSafe(option.getSaveTrm()) == months)
        .filter(option -> isValidRate(option.getIntrRate()) || isValidRate(option.getIntrRate2()))
        .max(Comparator.comparing((FinancialProductOptionResponse option) -> validRateOrNull(option.getIntrRate()),
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(option -> validRateOrNull(option.getIntrRate2()),
                Comparator.nullsFirst(Comparator.naturalOrder())))
        .orElse(null);
  }

  private boolean isValidRate(Double rate) {
    return rate != null && Double.isFinite(rate) && rate >= 0;
  }

  private Double validRateOrNull(Double rate) {
    return isValidRate(rate) ? rate : null;
  }

  private int parseMonthsSafe(String saveTrm) {
    try {
      return Integer.parseInt(saveTrm.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private List<FinancialProductResponse> getProducts(String productType, String bankType) {
    String normalized = normalizeBankType(bankType);

    List<FinancialProductResponse> items = switch (normalized) {
      case "savings" -> finlifeCacheService.getCached(
          FinlifeCacheService.cacheKey(productType, FinlifeCacheService.TOP_FIN_GRP_SAVINGS_BANK));
      case "all" -> {
        List<FinancialProductResponse> merged = new ArrayList<>();
        merged.addAll(finlifeCacheService.getCached(
            FinlifeCacheService.cacheKey(productType, FinlifeCacheService.TOP_FIN_GRP_BANK)));
        merged.addAll(finlifeCacheService.getCached(
            FinlifeCacheService.cacheKey(productType, FinlifeCacheService.TOP_FIN_GRP_SAVINGS_BANK)));
        yield merged;
      }
      default -> finlifeCacheService.getCached(
          FinlifeCacheService.cacheKey(productType, FinlifeCacheService.TOP_FIN_GRP_BANK));
    };

    // 최고우대금리(maxRate) 높은 순으로 정렬해서 보여준다. maxRate가 없는 상품은 맨 뒤로.
    return items.stream()
        .sorted(Comparator.comparing(
            FinancialProductResponse::getMaxRate,
            Comparator.nullsLast(Comparator.reverseOrder())))
        .collect(Collectors.toList());
  }

  private String normalizeBankType(String bankType) {
    if (bankType == null || bankType.isBlank()) return "bank";
    String trimmed = bankType.trim().toLowerCase();
    return switch (trimmed) {
      case "savings", "savingsbank", "저축은행" -> "savings";
      case "all", "전체" -> "all";
      default -> "bank";
    };
  }
}
