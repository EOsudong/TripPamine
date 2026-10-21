import { useState } from "react";
import { BedDouble, Search, Sparkles, Ticket } from "lucide-react";
import type { TravelPlan } from "../types";
import { suggestTravelKeyword } from "../utils/suggestTravelKeyword";
import AiRecommendAccommodationDeals from "./AiRecommendAccommodationDeals";
import AiRecommendPartnerDeals from "./AiRecommendPartnerDeals";
import AiRecommendSmartDeals from "./AiRecommendSmartDeals";
import "./AiRecommendDealsHub.css";

type DealMode = "smart" | "search" | "stay";

interface Props {
  plan: TravelPlan | null;
}

/** 서로 다른 상품 검색 UI를 하나의 영역으로 통합하고 선택한 검색 상태를 유지한다. */
export default function AiRecommendDealsHub({ plan }: Props) {
  // 여행을 선택했다면 맞춤 추천, 선택 전에는 자유 검색을 먼저 보여준다.
  const [mode, setMode] = useState<DealMode>(plan ? "smart" : "search");
  // 여행 선택이 해제된 경우 비활성 맞춤 추천 탭이 화면을 비우지 않도록 자유 검색으로 전환한다.
  const activeMode: DealMode = !plan && mode === "smart" ? "search" : mode;
  const suggestedKeyword = plan ? suggestTravelKeyword(plan).keyword : "";

  return (
    <section className="ar-hub" aria-label="마이리얼트립 제휴 상품">
      <div className="ar-hub-header">
        <div>
          <p className="ar-hub-eyebrow"><Ticket size={14} aria-hidden="true" /> MYREALTRIP PARTNER</p>
          <h3>실제 여행 상품 탐색</h3>
          <p>맞춤 추천과 자유 검색, 숙박 상품까지 한곳에서 비교할 수 있어요.</p>
        </div>
        <span className="ar-hub-label">제휴 상품</span>
      </div>
      <div className="ar-hub-switch" role="group" aria-label="제휴 상품 검색 방식">
        <button type="button" aria-pressed={activeMode === "smart"}
          disabled={!plan} onClick={() => setMode("smart")}>
          <Sparkles size={16} aria-hidden="true" /> 내 여행 맞춤 추천
        </button>
        <button type="button" aria-pressed={activeMode === "search"}
          onClick={() => setMode("search")}>
          <Search size={16} aria-hidden="true" /> 투어·티켓 검색
        </button>
        <button type="button" aria-pressed={activeMode === "stay"}
          onClick={() => setMode("stay")}>
          <BedDouble size={16} aria-hidden="true" /> 숙소 찾기
        </button>
      </div>
      {!plan && (
        <p className="ar-hub-hint">여행을 선택하면 예산에 맞춰 상품을 추천받을 수 있어요. 지금은 자유 검색을 이용해 주세요.</p>
      )}
      {/* hidden을 사용해 탭 전환 시 검색 결과와 검색어를 유지한다. 비활성 검색 UI는 표시되지 않는다. */}
      {plan && (
        <div className="ar-hub-pane" hidden={mode !== "smart"}>
          <AiRecommendSmartDeals plan={plan} />
        </div>
      )}
      <div className="ar-hub-pane" hidden={mode !== "search"}>
        <AiRecommendPartnerDeals planName={plan?.planName ?? ""} suggestedKeyword={suggestedKeyword} />
      </div>
      {/* 숙박 검색은 투어와 별도 API를 사용하며 날짜·성인 인원을 필수 확인한다. */}
      <div className="ar-hub-pane" hidden={mode !== "stay"}>
        <AiRecommendAccommodationDeals key={plan?.planId ?? "free"} plan={plan} suggestedKeyword={suggestedKeyword} />
      </div>
    </section>
  );
}