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