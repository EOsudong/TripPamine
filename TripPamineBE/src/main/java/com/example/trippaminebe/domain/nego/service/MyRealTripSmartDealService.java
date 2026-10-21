
package com.example.trippaminebe.domain.nego.service;

import com.example.trippaminebe.domain.nego.dto.request.MyRealTripSmartDealRequest;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripCatalogResponse;
import com.example.trippaminebe.domain.nego.dto.response.MyRealTripSmartDealResponse;
import com.example.trippaminebe.domain.travel.dto.TravelPlanResponse;
import com.example.trippaminebe.domain.travel.service.TravelPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/**
 * 검색 결과의 실제 판매 표시가와 여행 예산을 비교하는 1차 추천 엔진.
 * LLM 협상, 실제 추가 할인, 예약 가능 여부, 실시간 옵션 가격은 계산하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MyRealTripSmartDealService {

	private static final int RESULT_LIMIT = 6;
	private static final BigDecimal MAX_REVIEW_SCORE = BigDecimal.valueOf(5);

	private final TravelPlanService travelPlanService;
	private final MyRealTripCatalogService myRealTripCatalogService;

	public MyRealTripSmartDealResponse recommend(Long userId, MyRealTripSmartDealRequest request) {
		// 기존 여행 조회 서비스가 사용자 소유권과 삭제 여부를 검증한다.
		TravelPlanResponse plan = travelPlanService.findById(request.planId(), userId);

		BigDecimal budget = plan.getTotalBudget() == null
				? BigDecimal.ZERO : plan.getTotalBudget().max(BigDecimal.ZERO);

		BigDecimal ceiling = request.maxProductPrice() == null
				? budget : request.maxProductPrice().min(budget);

		String keyword = request.keyword().trim();

		// 상품 상한이 0원인 경우 유료 상품 추천을 하지 않으며 외부 API도 호출하지 않는다.
		if (ceiling.signum() == 0) {
			return new MyRealTripSmartDealResponse(
					plan.getPlanId(), plan.getPlanName(), keyword,
					budget, ceiling, 0, 0, 0,
					"SEARCH_SALE_PRICE", "RULE_BASED", List.of()
			);
		}

		// 제공사에서 반환한 첫 검색 페이지의 상품만 분석한다.
		MyRealTripCatalogResponse catalog = myRealTripCatalogService.searchTours(keyword);

		List<MyRealTripSmartDealResponse.Recommendation> matches = new ArrayList<>();
		Set<String> seenProductIds = new HashSet<>();

		for (MyRealTripCatalogResponse.Product product : catalog.items()) {
			if (product.salePrice() == null
					|| product.salePrice().signum() < 0
					|| product.salePrice().compareTo(ceiling) > 0) {
				continue;
			}

			// 동일한 상품 ID가 반복되어도 추천 목록에는 한 번만 추가한다.
			if (!seenProductIds.add(product.gid())) {
				continue;
			}

			matches.add(toRecommendation(product, ceiling));
		}

		// 추천 점수, 판매가, 상품 ID 순서로 결과를 안정적으로 정렬한다.
		matches.sort(Comparator
				.comparingInt(MyRealTripSmartDealResponse.Recommendation::matchScore)
				.reversed()
				.thenComparing(result -> result.product().salePrice())
				.thenComparing(result -> result.product().gid()));

		return new MyRealTripSmartDealResponse(
				plan.getPlanId(), plan.getPlanName(), keyword,
				budget, ceiling, catalog.totalCount(),
				catalog.items().size(), matches.size(),
				"SEARCH_SALE_PRICE", "RULE_BASED",
				List.copyOf(matches.subList(0, Math.min(RESULT_LIMIT, matches.size())))
		);
	}

	private MyRealTripSmartDealResponse.Recommendation toRecommendation(
			MyRealTripCatalogResponse.Product product,
			BigDecimal ceiling
	) {
		// 점수 100점: 가격 35 + 평점 30 + 후기 20 + 제공사 태그 15.
		double priceRatio = product.salePrice()
				.divide(ceiling, 6, RoundingMode.HALF_UP)
				.doubleValue();

		double pricePoints = 35 * Math.max(0, 1 - priceRatio);

		double rating = product.reviewScore() == null
				? 0
				: product.reviewScore()
				.max(BigDecimal.ZERO)
				.min(MAX_REVIEW_SCORE)
				.doubleValue();

		double ratingPoints = 30 * rating / 5;

		long reviewCount = product.reviewCount() == null
				? 0 : Math.max(0, product.reviewCount());

		double reviewPoints = 20 * Math.min(
				1, Math.log10(reviewCount + 1.0) / 4.0
		);

		List<String> tags = product.tags() == null
				? List.of() : product.tags();

		boolean instant = hasTag(tags, "즉시 확정");
		boolean freeCancel = hasTag(tags, "무료취소");
		boolean priceGuarantee = hasTag(tags, "최저가보장제");

		int tagPoints = (instant ? 7 : 0)
				+ (freeCancel ? 5 : 0)
				+ (priceGuarantee ? 3 : 0);

		int score = (int) Math.round(
				pricePoints + ratingPoints + reviewPoints + tagPoints
		);

		List<String> reasons = new ArrayList<>();
		reasons.add("설정한 상품 가격 상한 이내");

		if (product.reviewScore() != null && rating > 0 && reviewCount > 0) {
			reasons.add(String.format(
					Locale.KOREA,
					"평점 %.1f · 후기 %,d개",
					rating,
					reviewCount
			));
		}

		if (instant) reasons.add("제공사 표기: 즉시 확정");
		if (freeCancel) reasons.add("제공사 표기: 무료취소");
		if (priceGuarantee) reasons.add("제공사 표기: 최저가보장제");

		return new MyRealTripSmartDealResponse.Recommendation(
				product,
				score,
				List.copyOf(reasons)
		);
	}

	private boolean hasTag(List<String> tags, String expected) {
		return tags.stream().anyMatch(expected::equals);
	}
}