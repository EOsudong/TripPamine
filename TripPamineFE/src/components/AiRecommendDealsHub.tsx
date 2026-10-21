import { useState } from "react";
import { Search, Sparkles, Ticket } from "lucide-react";
import type { TravelPlan } from "../types";
import { getTravelDealKeyword } from "../utils/travelDealKeyword";
import AiRecommendPartnerDeals from "./AiRecommendPartnerDeals";
import AiRecommendSmartDeals from "./AiRecommendSmartDeals";
import "./AiRecommendDealsHub.css";

type DealMode = "smart" | "search";

interface Props {
  plan: TravelPlan | null;
}

/** 서로 다른 상품 검색 UI를 하나의 영역으로 통합하고 선택한 검색 상태를 유지한다. */
export default function AiRecommendDealsHub({ plan }: Props) {
  // 여행을 선택했다면 맞춤 추천, 선택 전에는 자유 검색을 먼저 보여준다.
  const [mode, setMode] = useState<DealMode>(plan ? "smart" : "search");
  const suggested = getTravelDealKeyword(plan);

  return (
    <section className="ar-hub" aria-label="마이리얼트립 제휴 상품">
      <div className="ar-hub-header">
        <div>
          <p className="ar-hub-eyebrow"><Ticket size={14} aria-hidden="true" /> MYREALTRIP PARTNER</p>
          <h3>실제 여행 상품 탐색</h3>
          <p>맞춤 추천과 자유 검색을 한곳에서 확인할 수 있어요.</p>
        </div>
        <span className="ar-hub-label">제휴 상품</span>
      </div>
      <div className="ar-hub-switch" role="group" aria-label="제휴 상품 검색 방식">
        <button type="button" aria-pressed={mode === "smart"}
          disabled={!plan} onClick={() => setMode("smart")}>
          <Sparkles size={16} aria-hidden="true" /> 내 여행 맞춤 추천
        </button>
        <button type="button" aria-pressed={mode === "search"}
          onClick={() => setMode("search")}>
          <Search size={16} aria-hidden="true" /> 자유 상품 검색
        </button>
      </div>
      {!plan ? (
        <p className="ar-hub-hint">여행을 선택하면 예산에 맞춰 상품을 추천받을 수 있어요. 지금은 자유 검색을 이용해 주세요.</p>
      ) : suggested ? (
        <p className="ar-hub-hint">
          검색어 제안: <strong>{suggested.keyword}</strong> · {suggested.source === "LOCATION_CODE"
            ? "등록된 여행 지역 코드 기준" : "여행명에 포함된 지역 기준"}.
          검색어를 확인하고 원하는 상품을 조회해 주세요.
        </p>
      ) : (
        <p className="ar-hub-hint">여행 지역을 확정할 수 없어 자동 입력하지 않았어요. 아래 검색창에 지역 또는 활동명을 입력해 주세요.</p>
      )}
      {/* hidden을 사용해 탭 전환 시 검색 결과와 검색어를 유지한다. 비활성 검색 UI는 표시되지 않는다. */}
      {plan && (
        <div className="ar-hub-pane" hidden={mode !== "smart"}>
          <AiRecommendSmartDeals plan={plan} />
        </div>
      )}
      <div className="ar-hub-pane" hidden={mode !== "search"}>
        <AiRecommendPartnerDeals planName={suggested?.keyword ?? ""} />
      </div>
    </section>
  );
}
