import { api } from "./axios"
import type { TourDetail, TourItem, TourMainCategoryKey } from "../types"

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

/**
 * 카드(TourItemCard) 클릭 시 상세 페이지(/tour/:categoryKey/:contentId)에서 호출.
 * contentTypeId는 목록 조회 때 이미 알고 있는 값을 넘겨주는 보조 파라미터로,
 * 백엔드가 캐시에서 항목을 못 찾았을 때만 사용한다 (없어도 동작함).
 */
export const getTourDetailApi = async (
    contentId: string,
    contentTypeId?: string | null,
): Promise<TourDetail> => {
    const response = await api.get<TourDetail>(`/tour/detail/${contentId}`, {
        params: contentTypeId ? { contentTypeId } : undefined,
    })
    return response.data
}



