// 프로젝트 전역에서 재사용하는 타입 모음.
// 여행지/축제 데이터 구조와, AI 플래너 폼 입력값 구조를 여기서 한 번에 관리합니다.

// 인기 여행지 하나의 데이터 구조 (data/destinations.ts에서 사용)
export interface Destination {
  id: string
  name: string
  region: string
  tag: string
  theme: string
  reason: string
  description: string
  img: string
}

// 축제/행사 하나의 데이터 구조 (data/festivals.ts에서 사용)
export interface Festival {
  id: string
  name: string
  location: string
  startDate: string
  endDate: string
  category: string
  status: "ongoing" | "upcoming" | "ended"
  dday: number
  description: string
  img: string
  tags: string[]
}

// Hero.tsx의 AI 여행 조건 입력 폼 상태 구조
export interface PlannerForm {
  people: string
  startDate: string
  endDate: string
  budget: string
  travelType: string
  extra: string
}

// 여행 플래너 기능 merge
// 여행 계획 데이터 타입 (travel-project)
export interface TravelPlan {
  planId: number
  planName: string
  totalBudget: number
  companionType: string | null
  blindYn: string
  startDate: string | null
  endDate: string | null
}

// 여행 계획 입력 폼 상태 타입
export interface TravelPlanFormState {
  planName: string
  totalBudget: string
  companionType: string
  blindYn: string
  startDate: string
  endDate: string
}

// 한국관광공사 오픈API(한국관광콘텐츠랩) 연동 데이터 하나의 구조.
// 백엔드 /tour/festivals, /tour/destinations, /tour/industry가 공통으로 이 형태로 내려줍니다.
// (data/tourCategories.ts의 소분류 필터, components/TourItemCard.tsx, pages/TourCategoryPage.tsx에서 사용)
export interface TourItem {
  contentId: string
  contentTypeId: string
  title: string
  category: string
  address: string | null
  imageUrl: string | null
  mapX: number | null
  mapY: number | null
  eventStartDate: string | null // yyyyMMdd, 축제만 값이 있음
  eventEndDate: string | null // yyyyMMdd, 축제만 값이 있음
  status: "ongoing" | "upcoming" | null // 축제만 값이 있음
}

// 사이드바 "정보" 그룹에 노출되는 3개 대분류 키
export type TourMainCategoryKey = "festivals" | "destinations" | "industry"