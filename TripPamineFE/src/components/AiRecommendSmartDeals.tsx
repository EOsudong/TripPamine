import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowUpRight, BadgeCheck, Loader2, Search, Sparkles, Star, Wallet } from "lucide-react";
import { myRealTripMylinkApi } from "../api/myrealtripMylink";
import {
  myRealTripSmartDealsApi,
  type MyRealTripSmartDealResponse,
} from "../api/myrealtripSmartDeals";
import type { TravelPlan } from "../types";
import { getTravelDealKeyword } from "../utils/travelDealKeyword";
import "./AiRecommendSmartDeals.css";

interface Props {
  plan: TravelPlan;
}

// 제휴사 검색 결과에서 확인된 상품 주소만 링크 발급 요청에 사용한다.
function verifiedProductUrl(value: string | null, gid: string): string | null {
  if (!value || !/^[0-9]{1,20}$/.test(gid)) return null;
  try {
    const uri = new URL(value);
    return uri.protocol === "https:" && uri.hostname === "experiences.myrealtrip.com" &&
      uri.pathname === `/products/${gid}` && !uri.search && !uri.hash &&
      !uri.port && !uri.username && !uri.password ? uri.href : null;
  } catch {
    return null;
  }
}

function verifiedAffiliateUrl(value: string): string | null {
  try {
    const uri = new URL(value);
    return uri.protocol === "https:" && uri.hostname === "myrealt.rip" &&
      !uri.port && !uri.username && !uri.password && !uri.hash ? uri.href : null;
  } catch {
    return null;
  }
}

function verifiedImageUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const uri = new URL(value);
    return uri.protocol === "https:" ? uri.href : null;
  } catch {
    return null;
  }
}

function won(value: number): string {
  return `${value.toLocaleString("ko-KR")}원`;
}

/** 여행 계획의 총예산과 조회된 제휴사 시작가를 비교해 규칙 기반 추천을 보여준다. */
export default function AiRecommendSmartDeals({ plan }: Props) {
  // 확인된 지역 코드나 여행명에 명시된 지역만 검색어로 제안하고 API 호출은 하지 않는다.
  const [keyword, setKeyword] = useState(() => getTravelDealKeyword(plan)?.keyword ?? "");
  const [priceCeiling, setPriceCeiling] = useState("");
  const [result, setResult] = useState<MyRealTripSmartDealResponse | null>(null);
  const [error, setError] = useState("");
  const [linkError, setLinkError] = useState("");
  const [searching, setSearching] = useState(false);
  const [linkLoadingGid, setLinkLoadingGid] = useState<string | null>(null);
  const [preparedLinks, setPreparedLinks] = useState<Record<string, string>>({});
  const searchSequence = useRef(0);
  // React 상태가 갱신되기 전 연속 제출해도 API는 한 번만 호출한다.
  const searchBusyRef = useRef(false);
  const linkRequests = useRef(new Set<string>());

  // 여행을 바꾸거나 페이지를 떠난 뒤 늦게 돌아온 검색 결과는 무시한다.
  useEffect(() => () => { searchSequence.current += 1; }, []);

  const search = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const query = keyword.trim();
    if (!query || searchBusyRef.current) return;

    const budget = Number(plan.totalBudget);
    const limit = priceCeiling.trim() ? Number(priceCeiling) : undefined;
    if (!Number.isFinite(budget) || budget <= 0) {
      setError("여행 예산이 0원이거나 올바르지 않습니다. 여행 계획에서 예산을 수정해주세요.");
      return;
    }
    if (limit !== undefined && (!Number.isSafeInteger(limit) || limit <= 0 || limit > budget)) {
      setError("상품별 최대 금액은 1원 이상, 여행 총예산 이하로 입력해주세요.");
      return;
    }

    searchBusyRef.current = true;
    const sequence = ++searchSequence.current;
    setSearching(true);
    setError("");
    setLinkError("");
    setResult(null);
    setPreparedLinks({});
    try {
      const response = await myRealTripSmartDealsApi.recommend({
        planId: plan.planId,
        keyword: query,
        ...(limit !== undefined ? { maxProductPrice: limit } : {}),
      });
      if (sequence === searchSequence.current) {
        // 서버 수정 전 구버전 응답이 들어와도 동일 gid가 두 번 렌더링되지 않게 막는다.
        const seen = new Set<string>();
        setResult({
          ...response,
          items: response.items.filter(({ product }) => {
            if (seen.has(product.gid)) return false;
            seen.add(product.gid);
            return true;
          }),
        });
      }
    } catch {
      if (sequence === searchSequence.current) {
        setError("상품 추천 정보를 불러오지 못했어요. 로그인 상태와 검색어를 확인해 주세요.");
      }
    } finally {
      searchBusyRef.current = false;
      if (sequence === searchSequence.current) setSearching(false);
    }
  };

  const openProduct = async (gid: string, productUrl: string) => {
    if (linkRequests.current.has(gid)) return;
    linkRequests.current.add(gid);
    setLinkLoadingGid(gid);
    setLinkError("");
    // 비동기 호출 전에 클릭으로 열린 탭을 만들어 팝업 차단을 최소화한다.
    const popup = window.open("about:blank", "_blank");
    if (popup) {
      popup.opener = null;
      popup.document.title = "제휴 링크 연결 중";
      popup.document.body.textContent = "안전한 마이리얼트립 제휴 링크를 준비 중입니다.";
    }
    try {
      const raw = await myRealTripMylinkApi.create(gid, productUrl);
      const link = verifiedAffiliateUrl(raw);
      if (!link) throw new Error("Invalid affiliate link");
      if (popup && !popup.closed) {
        popup.location.replace(link);
      } else {
        setPreparedLinks((current) => ({ ...current, [gid]: link }));
        setLinkError("새 창이 차단되었어요. 상품 카드의 '준비된 링크 열기'를 이용해주세요.");
      }
    } catch {
      if (popup && !popup.closed) popup.close();
      setLinkError("제휴 링크 발급에 실패했습니다. 일반 상품 주소로 자동 대체하지 않습니다.");
    } finally {
      linkRequests.current.delete(gid);
      setLinkLoadingGid(null);
    }
  };

  return (
    <section className="ar-smart tp-card-dark" aria-labelledby="ar-smart-title">
      <div className="ar-smart-header">
        <div>
          <p className="ar-smart-eyebrow"><Sparkles size={15} /> TRIPPAMINE SMART DEAL</p>
          <h2 id="ar-smart-title">내 여행 맞춤 제휴 상품 추천</h2>
          <p className="ar-smart-subtitle">여행 계획의 예산에 맞는 실제 상품을 골라 가격·평점·후기로 순위를 매겨요.</p>
        </div>
        <span className="ar-smart-budget"><Wallet size={15} /> 여행 총예산 {won(plan.totalBudget)}</span>
      </div>

      <form className="ar-smart-form" onSubmit={(event) => void search(event)}>
        <label className="ar-smart-field">
          <span>지역·활동 검색어</span>
          <input type="search" maxLength={80} autoComplete="off" value={keyword}
            onChange={(event) => setKeyword(event.target.value)} placeholder="예: 제주, 부산, 스노클링" />
        </label>
        <label className="ar-smart-field ar-smart-field--price">
          <span>상품 1개 가격 상한 (원 · 선택)</span>
          <input type="number" min="1" step="1" max={Math.max(1, plan.totalBudget)}
            value={priceCeiling} onChange={(event) => setPriceCeiling(event.target.value)}
            placeholder="미입력 시 여행 총예산 적용" />
        </label>
        <button type="submit" disabled={searching || !keyword.trim()}>
          {searching ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
          {searching ? "추천 계산 중…" : "맞춤 추천 보기"}
        </button>
      </form>
      <div className="ar-smart-quickwords" role="group" aria-label="빠른 검색 지역">
        {(["제주", "부산", "서울", "강릉"] as const).map((city) => (
          <button type="button" key={city} aria-pressed={keyword === city}
            onClick={() => setKeyword(city)}>{city}</button>
        ))}
      </div>
      <p className="ar-smart-note">
        현재는 실시간 협상 AI가 아닌 <strong>규칙 기반 1차 추천</strong>입니다. 상품 가격은 검색 결과의
        표시 시작가이며 인원별 총액, 여행 날짜 예약 가능 여부, 실제 할인액은 검증되지 않았습니다.
        상품 선택 시 제휴사에서 최종 정보를 확인하세요. 제휴 링크 구매에 따른 수수료가 발생할 수 있습니다.
      </p>

      {searching && (
        <div className="ar-smart-loading" role="status" aria-live="polite">
          <Loader2 size={16} className="animate-spin" aria-hidden="true" />
          상품 가격과 후기 정보를 비교하고 있어요…
        </div>
      )}
      {error && <p className="ar-smart-message ar-smart-error" role="alert">{error}</p>}
      {linkError && <p className="ar-smart-message ar-smart-error" role="alert">{linkError}</p>}

      {result && (
        <div className="ar-smart-results">
          <div className="ar-smart-results-head">
            <div>
              <p className="ar-smart-eyebrow"><BadgeCheck size={14} /> PRICE-FIT RANKING</p>
              <h3>‘{result.keyword}’ 추천 상품</h3>
              <p>상품 가격 상한 {won(result.appliedPriceCeiling)} · 첫 조회 {result.evaluatedCount}개 분석 · 예산 충족 {result.eligibleCount}개</p>
            </div>
            <span>상위 {result.items.length}개</span>
          </div>
          {result.items.length === 0 ? (
            <p className="ar-smart-message">
              첫 검색 결과에서 예산에 맞는 상품을 찾지 못했어요. 상품 가격 상한을 올리거나 검색어를 바꿔보세요.
            </p>
          ) : (
            <div className="ar-smart-grid">
              {result.items.map(({ product, matchScore, reasons }, index) => {
                const url = verifiedProductUrl(product.productUrl, product.gid);
                const imageUrl = verifiedImageUrl(product.imageUrl);
                return (
                  <article className="ar-smart-card" key={product.gid}>
                    <div className="ar-smart-image">
                      {imageUrl ? <img src={imageUrl} loading="lazy" alt="" /> : <span>이미지 준비 중</span>}
                      <span className="ar-smart-rank">#{index + 1}</span>
                    </div>
                    <div className="ar-smart-card-body">
                      <div className="ar-smart-card-top">
                        <span>{product.category ?? "여행 상품"}</span>
                        <strong>추천 점수 {matchScore}/100</strong>
                      </div>
                      <h4>{product.itemName}</h4>
                      <p className="ar-smart-price">{product.priceDisplay ?? won(product.salePrice)}</p>
                      {product.reviewScore !== null && (
                        <p className="ar-smart-review"><Star size={14} /> {product.reviewScore.toFixed(1)}
                          {product.reviewCount !== null ? ` · 후기 ${product.reviewCount.toLocaleString("ko-KR")}개` : ""}</p>
                      )}
                      <ul className="ar-smart-reasons">
                        {reasons.slice(0, 4).map((reason) => <li key={reason}>{reason}</li>)}
                      </ul>
                      {url ? (
                        <button type="button" className="ar-smart-open"
                          disabled={linkLoadingGid === product.gid}
                          onClick={() => void openProduct(product.gid, url)}>
                          {linkLoadingGid === product.gid ? "링크 준비 중…" : "제휴 상품 보기"}
                          <ArrowUpRight size={15} />
                        </button>
                      ) : <p className="ar-smart-no-link">유효한 상품 주소가 없습니다.</p>}
                      {preparedLinks[product.gid] && (
                        <a className="ar-smart-ready" href={preparedLinks[product.gid]}
                          target="_blank" rel="noopener noreferrer">준비된 링크 열기 <ArrowUpRight size={14} /></a>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
          <p className="ar-smart-footer">
            전체 검색 결과 {result.totalSearchResults.toLocaleString("ko-KR")}개 중 첫 조회분만 평가했어요.
            추천 점수는 가격 여유(35)·평점(30)·후기 수(20)·제공사 태그(15)의 합산입니다.
            날짜·인원별 예약 가능 여부는 포함되지 않아요.
          </p>
        </div>
      )}
    </section>
  );
}
