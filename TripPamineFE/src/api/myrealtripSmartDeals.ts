import { api } from "./axios";
import type { MyRealTripProduct } from "./myrealtripCatalog";

/** 실제 가격 기반 규칙 추천 결과. 판매자 네고 또는 결제 승인 데이터는 아니다. */
export interface MyRealTripSmartDealRecommendation {
  product: MyRealTripProduct;
  matchScore: number;
  reasons: string[];
}

export interface MyRealTripSmartDealResponse {
  planId: number;
  planName: string;
  keyword: string;
  tripBudget: number;
  appliedPriceCeiling: number;
  totalSearchResults: number;
  evaluatedCount: number;
  eligibleCount: number;
  priceBasis: "SEARCH_SALE_PRICE";
  rankingType: "RULE_BASED";
  items: MyRealTripSmartDealRecommendation[];
}

export interface MyRealTripSmartDealRequest {
  planId: number;
  keyword: string;
  maxProductPrice?: number;
}

export const myRealTripSmartDealsApi = {
  recommend: async (request: MyRealTripSmartDealRequest): Promise<MyRealTripSmartDealResponse> => {
    const response = await api.post<MyRealTripSmartDealResponse>(
      "/nego/partners/myrealtrip/recommendations",
      request,
    );
    return response.data;
  },
};
