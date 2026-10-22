import { useEffect, useRef, useState, type FormEvent } from "react";
import { ArrowUpRight, BedDouble, CalendarDays, Loader2, Search, Users, Star } from "lucide-react";
import type { TravelPlan } from "../types";
import { myRealTripAccommodationApi } from "../api/myrealtripAccommodation";
import { myRealTripMylinkApi } from "../api/myrealtripMylink";
import { normalizeAccommodation, type AccommodationResult } from "../utils/normalizeAccommodation";
import type { AccommodationSearchRequest } from "../api/myrealtripAccommodation";
import PartnerSearchPagination from "./PartnerSearchPagination";
import "./AiRecommendAccommodationDeals.css";

interface Props {
  plan: TravelPlan | null;
  suggestedKeyword: string;
}

const datePattern = /^\d{4}-\d{2}-\d{2}$/;

function validDate(value: string | null | undefined): string {
  return value && datePattern.test(value) ? value : "";
}

function nightCount(start: string, end: string): number {
  const startMs = Date.parse(`${start}T00:00:00Z`);
  const endMs = Date.parse(`${end}T00:00:00Z`);
  return Math.round((endMs - startMs) / 86_400_000);
}

function won(value: number): string {
  return `${value.toLocaleString("ko-KR")}원`;
}

function safeMylinkUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === "https:"
      && url.hostname === "myrealt.rip"
      && !url.port && !url.username && !url.password && !url.hash
      ? url.href : null;
  } catch {
    return null;
  }
}

/**
 * 숙소 검색 성공 응답의 상품 ID, 가격, 평점, 원본 URL을 표시한다.
 * 숙소의 최종 결제 금액과 MyLink 추적 성공 여부는 별개로 안내한다.
 */
export default function AiRecommendAccommodationDeals({ plan, suggestedKeyword }: Props) {
  const [keyword, setKeyword] = useState(suggestedKeyword);
  const [checkIn, setCheckIn] = useState(validDate(plan?.startDate));
  const [checkOut, setCheckOut] = useState(validDate(plan?.endDate));
  const [adultCount, setAdultCount] = useState("2");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [searched, setSearched] = useState("");
  const [results, setResults] = useState<AccommodationResult | null>(null);
  const [linkLoadingId, setLinkLoadingId] = useState<string | null>(null);
  const [linkError, setLinkError] = useState("");
  const [preparedLinks, setPreparedLinks] = useState<Record<string, string>>({});
  const inFlight = useRef(false);
  const requestSequence = useRef(0);
  const linkInFlight = useRef(new Set<string>());
  // 다음 페이지를 누른 뒤 검색 입력값이 바뀌더라도 직전에 성공한 조건으로 조회한다.
  const lastRequestRef = useRef<AccommodationSearchRequest | null>(null);
  const resultsRef = useRef<HTMLDivElement>(null);

  // 여행을 바꾸거나 언마운트한 뒤 도착한 이전 검색 응답을 무시한다.
  useEffect(() => () => { requestSequence.current += 1; }, []);

  /** 숙박은 제공사 응답 기준 0부터 시작하며, 조건 변경 시 첫 페이지부터 다시 검색한다. */
  const loadPage = async (request: AccommodationSearchRequest, page: number, freshSearch: boolean) => {
    if (inFlight.current) return;
    inFlight.current = true;
    const sequence = ++requestSequence.current;
    setLoading(true);
    setError("");
    setLinkError("");
    if (freshSearch) setResults(null);
    try {
      const response = await myRealTripAccommodationApi.search({ ...request, page });
      if (sequence !== requestSequence.current) return;
      const normalized = normalizeAccommodation(response);
      if (normalized.schemaUnverified || normalized.page !== page) {
        setError("요청한 숙박 페이지의 응답을 확인할 수 없어요. 제휴사 API의 페이지 지원 여부를 확인해주세요.");
        return;
      }
      lastRequestRef.current = { ...request, page };
      setSearched(request.keyword);
      setResults(normalized);
      setPreparedLinks({});
      if (!freshSearch) resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch {
      if (sequence === requestSequence.current) {
        setError("숙박 상품 페이지를 불러오지 못했어요. 제휴 API 권한·페이지·서버 로그를 확인해주세요.");
      }
    } finally {
      inFlight.current = false;
      if (sequence === requestSequence.current) setLoading(false);
    }
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (inFlight.current) return;
    const query = keyword.trim();
    const adults = Number(adultCount);
    const nights = nightCount(checkIn, checkOut);
    if (!query || query.length > 80) {
      setError("지역 또는 숙소 이름을 80자 이내로 입력해주세요.");
      return;
    }
    if (!datePattern.test(checkIn) || !datePattern.test(checkOut) || !Number.isFinite(nights) || nights <= 0) {
      setError("체크인과 체크아웃을 올바르게 선택해주세요. 체크아웃은 체크인 다음 날 이후여야 합니다.");
      return;
    }
    const today = new Date();
    const localToday = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    if (checkIn < localToday) {
      setError("지난 날짜의 숙소는 검색할 수 없어요. 체크인 날짜를 다시 선택해주세요.");
      return;
    }
    if (!Number.isInteger(adults) || adults < 1 || adults > 20) {
      setError("성인 인원은 1~20명으로 입력해주세요.");
      return;
    }
    void loadPage({ keyword: query, checkIn, checkOut, adultCount: adults }, 0, true);
  };

  const changePage = (visiblePage: number) => {
    const request = lastRequestRef.current;
    if (!request || !results || visiblePage === results.page + 1 || visiblePage < 1 || visiblePage > 200) return;
    void loadPage(request, visiblePage - 1, false);
  };

  const openStay = async (itemId: string, productUrl: string) => {
    if (linkInFlight.current.has(itemId)) return;
    linkInFlight.current.add(itemId);
    setLinkLoadingId(itemId);
    setLinkError("");

    // 브라우저 팝업 차단을 피하려면 사용자 클릭 시점에 탭을 먼저 확보해야 한다.
    const popup = window.open("about:blank", "_blank");
    if (popup) {
      popup.opener = null;
      popup.document.title = "마이리얼트립 숙박 제휴 링크 연결 중";
      popup.document.body.textContent = "공식 제휴 추적 링크 발급을 확인하고 있습니다.";
    }

    try {
      const responseUrl = await myRealTripMylinkApi.create(itemId, productUrl);
      const link = safeMylinkUrl(responseUrl);
      if (!link) throw new Error("Invalid accommodation MyLink response");
      if (popup && !popup.closed) {
        popup.location.replace(link);
      } else {
        setPreparedLinks((current) => ({ ...current, [itemId]: link }));
        setLinkError("팝업이 차단됐어요. 상품 카드에 표시된 제휴 링크를 직접 열어주세요.");
      }
    } catch {
      if (popup && !popup.closed) popup.close();
      setLinkError("숙박 제휴 링크 발급에 실패했어요. 발급 권한 및 MyLink 실제 응답을 확인해주세요. 일반 상품 URL로 자동 대체하지 않습니다.");
    } finally {
      linkInFlight.current.delete(itemId);
      setLinkLoadingId(null);
    }
  };

  const nights = nightCount(checkIn, checkOut);
  const totalPages = results && results.size > 0
    ? Math.min(200, Math.max(1, Math.ceil(results.totalCount / results.size)))
    : 1;

  return (
    <section className="ar-stay" aria-labelledby="ar-stay-title">
      <div className="ar-stay-header">
        <div>
          <p className="ar-hub-eyebrow"><BedDouble size={14} aria-hidden="true" /> MYREALTRIP ACCOMMODATION</p>
          <h2 id="ar-stay-title">숙박 상품 찾기</h2>
          <p>지역·날짜·성인 수를 기준으로 호텔과 리조트를 비교할 수 있어요.</p>
        </div>
        <span className="ar-stay-tag">호텔 · 리조트 · 숙소</span>
      </div>

      <form className="ar-stay-form" onSubmit={(event) => void submit(event)}>
        <label className="ar-stay-field ar-stay-field--wide">
          <span><Search size={14} /> 지역·숙소명</span>
          <input type="search" maxLength={80} value={keyword} onChange={(event) => setKeyword(event.target.value)}
            placeholder="예: 제주, 부산 해운대" autoComplete="off" required />
        </label>
        <label className="ar-stay-field">
          <span><CalendarDays size={14} /> 체크인</span>
          <input type="date" value={checkIn} onChange={(event) => setCheckIn(event.target.value)} required />
        </label>
        <label className="ar-stay-field">
          <span><CalendarDays size={14} /> 체크아웃</span>
          <input type="date" min={checkIn || undefined} value={checkOut} onChange={(event) => setCheckOut(event.target.value)} required />
        </label>
        <label className="ar-stay-field">
          <span><Users size={14} /> 성인 인원</span>
          <input type="number" min={1} max={20} step={1} value={adultCount} onChange={(event) => setAdultCount(event.target.value)} required />
        </label>
        <button type="submit" className="ar-stay-search" disabled={loading || !keyword.trim()}>
          {loading ? <Loader2 className="animate-spin" size={16} /> : <Search size={16} />}
          {loading ? "숙소 검색 중…" : "숙소 검색"}
        </button>
      </form>

      <div className="ar-stay-context">
        <span>{Number.isFinite(nights) && nights > 0 ? `${nights}박` : "날짜 선택 필요"}</span>
        <span>성인 {adultCount || "-"}명</span>
        {plan && <span>여행 총예산 {won(plan.totalBudget)}</span>}
      </div>
      <p className="ar-stay-disclaimer">
        표시가는 검색 시점의 숙소 판매 표시가이며 1박 가격 또는 최종 결제 총액으로 확정할 수 없습니다.
        숙박 조건·객실 옵션·예약 가능 여부는 구매처에서 확인하세요. 0원은 무료가 아닌 가격 미확인으로 표시합니다.
        제휴 링크를 통해 확인하고 예약이 가능합니다.
      </p>

      {loading && <p className="ar-stay-message" role="status">마이리얼트립에서 숙소를 확인하고 있어요…</p>}
      {error && <p className="ar-stay-error" role="alert">{error}</p>}
      {linkError && <p className="ar-stay-error" role="alert">{linkError}</p>}

      {results && (
        <div ref={resultsRef} className="ar-stay-results" aria-live="polite">
          <div className="ar-stay-results-title">
            <h3>‘{searched}’ 숙박 검색 결과</h3>
            <span>전체 {results.totalCount.toLocaleString("ko-KR")}개 · {results.page + 1}페이지 · 현재 {results.cards.length}개 표시</span>
          </div>
          {results.cards.length === 0 && <p className="ar-stay-message">검색된 숙소가 없어요. 지역이나 날짜를 바꿔보세요.</p>}
          <div className="ar-stay-grid">
            {results.cards.map((stay) => (
              <article key={stay.key} className="ar-stay-card">
                <div className="ar-stay-image">
                  {stay.imageUrl ? <img src={stay.imageUrl} alt="" loading="lazy" /> : <BedDouble size={28} aria-hidden="true" />}
                </div>
                <div className="ar-stay-card-body">
                  <div className="ar-stay-card-badges">
                    <span className="ar-stay-card-label">숙박</span>
                    {stay.starRating !== null && <span className="ar-stay-stars">{stay.starRating}성급</span>}
                  </div>
                  <h4>{stay.name}</h4>
                  {stay.price !== null ? (
                    <div className="ar-stay-pricing">
                      {stay.originalPrice !== null && <del>{won(stay.originalPrice)}</del>}
                      <strong>{won(stay.price)}</strong>
                      <small>검색 기준 판매 표시가</small>
                    </div>
                  ) : (
                    <div className="ar-stay-pricing ar-stay-pricing--unknown">
                      <strong>가격 확인 필요</strong>
                      <small>무료 숙박을 뜻하지 않아요</small>
                    </div>
                  )}
                  {stay.reviewScore !== null && (
                    <p className="ar-stay-review"><Star size={13} aria-hidden="true" />
                      {stay.reviewScore.toFixed(1)}점
                      {stay.reviewCount !== null ? ` · 후기 ${stay.reviewCount.toLocaleString("ko-KR")}개` : ""}
                    </p>
                  )}
                  <span className="ar-stay-card-note">객실·기간별 최종 가격은 예약 화면에서 확인</span>
                  {/* 콘텐츠 글자 수와 관계없이 모든 카드의 CTA가 행 하단에 위치한다. */}
                  <div className="ar-stay-card-actions">
                    {stay.productUrl ? (
                      <>
                        <button type="button" className="ar-stay-link"
                          disabled={linkLoadingId === stay.itemId}
                          onClick={() => { if (stay.productUrl) void openStay(stay.itemId, stay.productUrl); }}>
                          {linkLoadingId === stay.itemId ? <><Loader2 size={14} className="animate-spin" /> 제휴 링크 확인 중…</>
                            : <>제휴 링크로 숙소 보기 <ArrowUpRight size={14} aria-hidden="true" /></>}
                        </button>
                        {preparedLinks[stay.itemId] && (
                          <a className="ar-stay-link ar-stay-link--ready" href={preparedLinks[stay.itemId]}
                            target="_blank" rel="noopener noreferrer">제휴 링크 직접 열기 <ArrowUpRight size={14} /></a>
                        )}
                      </>
                    ) : (
                      <span className="ar-stay-link ar-stay-link--disabled">공식 숙박 주소 확인 필요</span>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
          <PartnerSearchPagination currentPage={results.page + 1}
            totalPages={totalPages} loading={loading} onPageChange={changePage} />
          {results.totalCount > results.size * 200 && (
            <p className="ar-stay-disclaimer">제휴사 API 부하 방지를 위해 앞의 200페이지까지만 조회할 수 있습니다.</p>
          )}
        </div>
      )}
    </section>
  );
}
