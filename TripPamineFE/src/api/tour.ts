import { api } from "./axios"
import type { TourItem, TourMainCategoryKey } from "../types"

// 백엔드 /tour/* 엔드포인트 호출. 대분류(key)별로 실제 경로만 다르고 나머지는 동일합니다.
const TOUR_ENDPOINTS: Record<TourMainCategoryKey, string> = {
    festivals: "/tour/festivals",
    destinations: "/tour/destinations",
    industry: "/tour/industry",
}

/** 대분류(key) + 소분류(filter, 예: "전체"/"진행중"/"체험 관광")로 관광 데이터 목록 조회 */
export const getTourItemsApi = async (
    categoryKey: TourMainCategoryKey,
    filter: string,
): Promise<TourItem[]> => {
    const response = await api.get<TourItem[]>(TOUR_ENDPOINTS[categoryKey], {
        params: { filter },
    })
    return response.data
}
