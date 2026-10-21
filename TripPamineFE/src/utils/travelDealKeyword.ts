import type { TravelPlan } from "../types";

export interface DealKeywordSuggestion {
  keyword: string;
  source: "LOCATION_CODE" | "PLAN_NAME";
}

// 소스의 주석에 예시로 등장한 지역 코드만 보수적으로 매핑한다.
// 나머지 코드(ETC 포함)는 뜻을 추정하지 않고 여행명 또는 수동 검색으로 처리한다.
const verifiedLocationCodes: Record<string, string> = {
  JEJU: "제주",
};

// 여행명에 이 지역들이 명시되어 있을 때만 검색어를 제안한다.
const knownRegionNames = ["제주", "부산", "서울", "강릉"] as const;

/** 실제 지역을 확정하는 기능이 아니라 사용자에게 보여줄 검색어를 제안한다. */
export function getTravelDealKeyword(
  plan: TravelPlan | null,
): DealKeywordSuggestion | null {
  if (!plan) return null;

  const locationCode = plan.locationCd?.trim().toUpperCase() ?? "";
  const fromCode = verifiedLocationCodes[locationCode];
  if (fromCode) return { keyword: fromCode, source: "LOCATION_CODE" };

  const matches = knownRegionNames.filter((region) => plan.planName.includes(region));
  // 둘 이상의 지역이 들어간 여행명이라면 어느 쪽을 검색할지 사용자가 직접 결정한다.
  if (matches.length !== 1) return null;
  return { keyword: matches[0], source: "PLAN_NAME" };
}
