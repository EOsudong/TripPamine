import { api } from "./axios";

/** 실제 제휴 상품 검색 결과. 판매자 할인율이나 제휴 수수료를 추정하지 않는다. */
export interface MyRealTripProduct {
  gid: string;
  itemName: string;
  description: string | null;
  salePrice: number;
  priceDisplay: string | null;
  category: string | null;
  reviewScore: number | null;
  reviewCount: number | null;
  imageUrl: string | null;
  productUrl: string | null;
  deepLink: string | null;
  tags: string[];
}

export interface MyRealTripCatalogResponse {
  items: MyRealTripProduct[];
  totalCount: number;
  page: number;
  perPage: number;
  hasNextPage: boolean;
}

export const myRealTripCatalogApi = {
  searchTours: async (keyword: string, page = 1): Promise<MyRealTripCatalogResponse> => {
    if (!Number.isSafeInteger(page) || page < 1 || page > 200) {
      throw new Error("조회할 수 없는 페이지입니다.");
    }
    const normalized = keyword.trim();
    if (!normalized) {
      throw new Error("검색어를 입력해주세요.");
    }
    const response = await api.post<MyRealTripCatalogResponse>(
      "/nego/partners/myrealtrip/tours/search/normalized",
      { keyword: normalized, page },
    );
    return response.data;
  },
};