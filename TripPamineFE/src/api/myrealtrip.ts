import api from "./axios";

/**
 * 마이리얼트립 상품 검색 첫 연동.
 * 실계정 응답 스키마를 검증하기 전까지는 unknown으로 받는다.
 * API 키는 프론트에 전달하지 않고 Spring 서버에서만 사용한다.
 */
export const myRealTripApi = {
  searchTours: async (keyword: string): Promise<unknown> => {
    const normalized = keyword.trim();
    if (!normalized) {
      throw new Error("검색어를 입력해주세요.");
    }
    const response = await api.post<unknown>(
      "/nego/partners/myrealtrip/tours/search",
      { keyword: normalized },
    );
    return response.data;
  },
};
