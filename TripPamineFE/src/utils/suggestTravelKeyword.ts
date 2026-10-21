import type { TravelPlan } from "../types";

/** 추천 근거: 확인된 저장 코드, 여행명에서 발견한 단일 지역, 판단 불가. */
export type TravelKeywordSource = "LOCATION_CODE" | "PLAN_NAME" | "UNAVAILABLE";

export interface TravelKeywordSuggestion {
  keyword: string;
  source: TravelKeywordSource;
}

// 프로젝트 스냅샷에서 확인된 저장 코드만 매핑한다. 미확인 영문 코드는 추가하지 않는다.
const VERIFIED_LOCATION_CODES: Readonly<Record<string, string>> = {
  JEJU: "제주",
};

// 기존 여행 지도 REGION_MAP에 정의되어 있는 지역명을 검색어 선택용으로 재사용한다.
// 이 목록은 DB의 LOCATION_CD 허용 코드 목록이라는 뜻이 아니다.
export const TRAVEL_SEARCH_REGIONS = [
  "서울", "제주", "부산", "인천", "대구", "대전", "광주", "울산",
  "수원", "경주", "전주", "여수", "강릉", "속초", "양양", "가평", "춘천", "횡성",
] as const;

type KeywordPlan = Pick<TravelPlan, "planName" | "locationCd">;

/**
 * 여행 지역 후보를 반환한다. 정확한 저장 코드/한글명이 있으면 이를 우선하며,
 * 코드가 불명확할 때만 여행명에서 한 개 이상 등장하는 지역을 추출한다.
 * 여러 지역이 나오면 사용자가 선택해야 하므로 자동으로 하나를 정하지 않는다.
 */
export function getTravelRegionCandidates(plan: KeywordPlan): string[] {
  const rawCode = plan.locationCd?.trim() ?? "";
  const verified = VERIFIED_LOCATION_CODES[rawCode.toUpperCase()];
  if (verified) return [verified];

  if (TRAVEL_SEARCH_REGIONS.some((region) => region === rawCode)) {
    return [rawCode];
  }

  const planName = plan.planName ?? "";
  return TRAVEL_SEARCH_REGIONS.filter((region) => planName.includes(region));
}

/**
 * 자동 입력에는 확실한 지역 하나만 사용한다.
 * '서울 부산 여행'처럼 여러 지역이 나오면 검색어를 비워 사용자가 선택하게 한다.
 */
export function suggestTravelKeyword(plan: KeywordPlan): TravelKeywordSuggestion {
  const rawCode = plan.locationCd?.trim() ?? "";
  const verified = VERIFIED_LOCATION_CODES[rawCode.toUpperCase()];
  if (verified) return { keyword: verified, source: "LOCATION_CODE" };
  if (TRAVEL_SEARCH_REGIONS.some((region) => region === rawCode)) {
    return { keyword: rawCode, source: "LOCATION_CODE" };
  }

  const candidates = getTravelRegionCandidates(plan);
  return candidates.length === 1
    ? { keyword: candidates[0], source: "PLAN_NAME" }
    : { keyword: "", source: "UNAVAILABLE" };
}
