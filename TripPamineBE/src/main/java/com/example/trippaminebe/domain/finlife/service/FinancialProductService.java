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

    return candidates.stream()
        .map(product -> toRecommendation(product, normalizedMonths, amount))
        .filter(rec -> rec != null)
        .sorted(Comparator.comparing(
            FinancialRecommendationResponse::getMatchedIntrRate2,
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

    FinancialProductOptionResponse matched = pickClosestOption(product.getOptions(), months);
    if (matched == null) {
      return null;
    }

    Double rateForEstimate = matched.getIntrRate2() != null ? matched.getIntrRate2() : matched.getIntrRate();
    Double estimatedInterest = (amount != null && rateForEstimate != null)
        ? amount * (rateForEstimate / 100.0) * (months / 12.0)
        : null;

    boolean exactTerm = String.valueOf(months).equals(matched.getSaveTrm());
    String reason = exactTerm
        ? months + "개월 예치 기준으로 최고우대금리가 높은 상품이에요."
        : "요청하신 " + months + "개월과 가장 가까운 " + matched.getSaveTrm() + "개월 조건으로 비교했어요.";

    return FinancialRecommendationResponse.builder()
        .matchedTerm(matched.getSaveTrm())
        .matchedIntrRate(matched.getIntrRate())
        .matchedIntrRate2(matched.getIntrRate2())
        .estimatedInterest(estimatedInterest)
        .reason(reason)
        .product(product)
        .build();
  }

  // saveTrm(문자열, 개월수) 중 요청한 months와 절댓값 차이가 가장 작은 옵션을 고른다.
  // 숫자로 파싱이 안 되는 saveTrm은 매우 먼 값으로 취급해서 사실상 후순위로 밀어낸다.
  private FinancialProductOptionResponse pickClosestOption(
      List<FinancialProductOptionResponse> options, int months
  ) {
    return options.stream()
        .filter(option -> option.getSaveTrm() != null)
        .min(Comparator.comparingInt(option -> Math.abs(parseMonthsSafe(option.getSaveTrm()) - months)))
        .orElse(null);
  }

  private int parseMonthsSafe(String saveTrm) {
    try {
      return Integer.parseInt(saveTrm.trim());
    } catch (Exception e) {
      return Integer.MAX_VALUE / 2;
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
