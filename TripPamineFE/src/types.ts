// 프로젝트 전역에서 재사용하는 타입 모음.
// 여행지/축제 데이터 구조와, AI 플래너 폼 입력값 구조를 여기서 한 번에 관리합니다.

// 인기 여행지 하나의 데이터 구조 (data/destinations.ts에서 사용)
export interface Destination {
  id: string;
  name: string;
  region: string;
  tag: string;
  theme: string;
  reason: string;
  description: string;
  img: string;
}

// 축제/행사 하나의 데이터 구조 (data/festivals.ts에서 사용)
export interface Festival {
  id: string;
  name: string;
  location: string;
  startDate: string;
  endDate: string;
  category: string;
  status: "ongoing" | "upcoming" | "ended";
  dday: number;
  description: string;
  img: string;
  tags: string[];
}

// Hero.tsx의 AI 여행 조건 입력 폼 상태 구조
export interface PlannerForm {
  people: string;
  startDate: string;
  endDate: string;
  budget: string;
  travelType: string;
  extra: string;
}

// 여행 플래너 기능 merge
// 여행 계획 데이터 타입 (travel-project)
export interface TravelPlan {
  planId: number;
  planName: string;
  totalBudget: number;
  companionType: string | null;
  blindYn: string;
  startDate: string | null;
  endDate: string | null;
}

// 여행 계획 입력 폼 상태 타입
export interface TravelPlanFormState {
  planName: string;
  totalBudget: string;
  companionType: string;
  blindYn: string;
  startDate: string;
  endDate: string;
}

// 한국관광공사 오픈API(한국관광콘텐츠랩) 연동 데이터 하나의 구조.
// 백엔드 /tour/festivals, /tour/destinations, /tour/industry가 공통으로 이 형태로 내려줍니다.
// (data/tourCategories.ts의 소분류 필터, components/TourItemCard.tsx, pages/TourCategoryPage.tsx에서 사용)
export interface TourItem {
  contentId: string;
  contentTypeId: string;
  title: string;
  category: string;
  address: string | null;
  imageUrl: string | null;
  mapX: number | null;
  mapY: number | null;
  eventStartDate: string | null; // yyyyMMdd, 축제만 값이 있음
  eventEndDate: string | null; // yyyyMMdd, 축제만 값이 있음
  status: "ongoing" | "upcoming" | null; // 축제만 값이 있음
}

// 사이드바 "정보" 그룹에 노출되는 3개 대분류 키
export type TourMainCategoryKey = "festivals" | "destinations" | "industry";

// 마이페이지 "저장된 축제 및 행사 / 저장된 관광 여행지 / 저장된 관광 산업" 탭에서 쓰는 북마크 구조.
// 백엔드 /bookmarks가 내려주며, 북마크 시점의 카드 정보를 스냅샷으로 그대로 들고 있음.
export interface TourBookmark {
  categoryKey: TourMainCategoryKey;
  contentId: string;
  contentTypeId: string | null;
  title: string;
  category: string | null;
  address: string | null;
  imageUrl: string | null;
  eventStartDate: string | null;
  eventEndDate: string | null;
  status: "ongoing" | "upcoming" | null;
  createdAt: string;
}

// TourItemCard 클릭 시 이동하는 상세 페이지(/tour/:categoryKey/:contentId)에서 사용하는 구조.
// 백엔드 /tour/detail/{contentId}가 내려주며, TourItem의 필드 + 개요/전화번호/홈페이지가 추가됨.
export interface TourDetail {
  contentId: string;
  contentTypeId: string | null;
  title: string;
  category: string | null;
  address: string | null;
  imageUrl: string | null;
  mapX: number | null;
  mapY: number | null;
  tel: string | null;
  homepage: string | null;
  overview: string | null;
  eventStartDate: string | null; // yyyyMMdd, 축제만 값이 있음
  eventEndDate: string | null; // yyyyMMdd, 축제만 값이 있음
  status: "ongoing" | "upcoming" | null; // 축제만 값이 있음
}

// [금융상품 정보 추가]
// 금융감독원 오픈API(금융상품 한눈에, FINLIFE) 연동 데이터 구조.

// 상품 하나가 갖는 "기간(개월)별 금리" 옵션 한 줄
export interface FinancialProductOption {
  saveTrm: string | null; // 저축 기간(개월), 예: "12"
  intrRateTypeNm: string | null; // 이자율 종류, 예: "단리" | "복리"
  intrRate: number | null; // 기본금리(%)
  intrRate2: number | null; // 최고우대금리(%)
  rsrvTypeNm: string | null; // 적립유형, 예: "정액적립식" | "자유적립식" (적금만 값이 있음)
}

// 정기예금/적금 상품 하나의 데이터 구조
export interface FinancialProduct {
  finCoNo: string | null;
  korCoNm: string; // 금융회사명 (예: "국민은행")
  finPrdtCd: string | null;
  finPrdtNm: string; // 상품명
  productType: "deposit" | "saving";
  joinWay: string | null; // 가입방법
  joinMember: string | null; // 가입대상
  joinDenyLabel: "제한없음" | "서민전용" | "일부제한" | null; // 가입제한
  spclCnd: string | null; // 우대조건
  mtrtInt: string | null; // 만기 후 이자율 설명
  etcNote: string | null; // 기타 유의사항
  dclsMonth: string | null; // 공시 제출월 (yyyyMM)
  maxRate: number | null; // 옵션 중 최고우대금리 최댓값 (목록 정렬/카드 대표 금리 표시용)
  options: FinancialProductOption[];
}

// 조회 대상 업권. "bank"=시중은행(기본값), "savings"=저축은행
export type FinancialBankType = "bank" | "savings";

// [금융상품 추천 추가]
export interface FinancialRecommendation {
  matchedTerm: string | null; // 추천 근거로 사용한 기간(개월)
  matchedIntrRate: number | null; // 위 기간의 기본금리
  matchedIntrRate2: number | null; // 위 기간의 최고우대금리
  estimatedInterest: number | null; // 입력한 예치금액 기준 세전 예상이자(단리, 참고용). 금액을 안 넣으면 null
  reason: string; // 추천 이유 한 줄 설명
  product: FinancialProduct; // 상품 상세(옵션 전체 포함) — 카드 렌더링에 그대로 재사용
}

// [금융상품 북마크 추가]
export interface FinancialProductBookmark {
  bookmarkId: string; // `${finCoNo}_${finPrdtCd}` 형태의 고유 키
  finCoNo: string | null;
  finPrdtCd: string | null;
  korCoNm: string;
  finPrdtNm: string;
  productType: "deposit" | "saving";
  maxRate: number | null;
  joinWay: string | null;
  createdAt: string;
}
