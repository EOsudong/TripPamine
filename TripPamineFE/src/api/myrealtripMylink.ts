import { api } from "./axios";

/** 일반 상품 URL이 아닌, 제휴사가 발급한 추적 링크만 반환한다. */
export interface MyRealTripMylinkResponse {
  url: string;
}

export const myRealTripMylinkApi = {
  create: async (gid: string, productUrl: string): Promise<string> => {
    const response = await api.post<MyRealTripMylinkResponse>(
      "/nego/partners/myrealtrip/mylink",
      { gid, productUrl },
    );
    return response.data.url;
  },
};
