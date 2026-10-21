import { api } from "./axios";

/** 숙박 API의 상세 응답 필드가 미검증이므로, 검증 전에는 unknown으로 받아 파싱한다. */
export interface AccommodationSearchRequest {
  keyword: string;
  checkIn: string;
  checkOut: string;
  adultCount: number;
  /** 숙소 검색은 0부터 시작하며, 최초 검색에서 생략할 수 있다. */
  page?: number;
}

export const myRealTripAccommodationApi = {
  search: async (request: AccommodationSearchRequest): Promise<unknown> => {
    const response = await api.post<unknown>(
      "/nego/partners/myrealtrip/accommodations/search",
      request,
    );
    return response.data;
  },
};
