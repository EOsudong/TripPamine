import type { TourMainCategoryKey } from "../types"

// 사이드바 "정보" 그룹 + /tour/:categoryKey 페이지가 공통으로 사용하는 대분류/소분류 설정.
export interface TourMainCategory {
    key: TourMainCategoryKey
    label: string // 대분류 이름 (사이드바 메뉴, 페이지 타이틀에 사용)
    path: string // 라우트 경로
    icon: string
    description: string // 페이지 상단에 보여줄 한 줄 설명
    subCategories: string[] // 소분류 탭 목록. 항상 "전체"가 먼저 옵니다.
}

export const tourMainCategories: TourMainCategory[] = [
    {
        key: "festivals",
        label: "국내 축제 및 행사",
        path: "/tour/festivals",
        icon: "🎉",
        description: "한국관광공사 오픈API로 받아오는 전국 축제·행사 정보예요.",
        subCategories: ["전체", "진행중", "문화 예술", "체험", "음식 음료"],
    },
    {
        key: "destinations",
        label: "국내 관광 여행지",
        path: "/tour/destinations",
        icon: "🗺️",
        description: "테마별로 둘러보는 국내 관광지 정보예요.",
        subCategories: ["전체", "체험 관광", "역사 관광", "자연 관광", "문화 관광"],
    },
    {
        key: "industry",
        label: "국내 관광 산업",
        path: "/tour/industry",
        icon: "🏨",
        description: "숙박·음식·레저·쇼핑까지, 여행에 필요한 관광 산업 정보예요.",
        subCategories: ["전체", "숙박", "음식", "레저 스포츠", "쇼핑"],
    },
]

export function findTourMainCategory(key: string | undefined): TourMainCategory | undefined {
    return tourMainCategories.find((c) => c.key === key)
}
