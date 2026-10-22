import { useEffect, useRef, useState, type FormEvent } from "react";
import { myRealTripMylinkApi } from "../api/myrealtripMylink";
import { ArrowUpRight, Loader2, Search, Ticket, Star } from "lucide-react";
import {
  myRealTripCatalogApi,
  type MyRealTripCatalogResponse,
} from "../api/myrealtripCatalog";
import PartnerSearchPagination from "./PartnerSearchPagination";
import "./AiRecommendPartnerDeals.css";

interface AiRecommendPartnerDealsProps {
  planName: string;
  suggestedKeyword?: string;
}

function safeProductUrl(value: string | null, gid: string): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    if (
      url.protocol !== "https:" ||
      host !== "experiences.myrealtrip.com" ||
      url.pathname !== `/products/${gid}` ||
      url.search ||
      url.hash ||
      url.port ||
      url.username ||
      url.password
    ) {
      return null;
    }
    return url.href;
  } catch {
    return null;
  }
}

function safeMylinkUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "myrealt.rip" && !url.port && !url.username && !url.password
      ? url.href
      : null;
  } catch {
    return null;
  }
}

function safeImageUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" ? url.href : null;
  } catch {
    return null;
  }
}

/** 여행명이 실제 지역명과 다를 수 있으므로 자동 검색하지 않고 검색어만 제안한다. */
export default function AiRecommendPartnerDeals({ planName, suggestedKeyword }: AiRecommendPartnerDealsProps) {
  // 여행명이 지역과 다를 수 있으므로 미확인 여행명을 검색어로 넣지 않는다.
  const [keyword, setKeyword] = useState(suggestedKeyword ?? "");
  const [searchedKeyword, setSearchedKeyword] = useState("");
  const [catalog, setCatalog] = useState<MyRealTripCatalogResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [linkLoadingGid, setLinkLoadingGid] = useState<string | null>(null);
  const [linkError, setLinkError] = useState("");
  const [preparedLinks, setPreparedLinks] = useState<Record<string, string>>({});
  const linkRequestRef = useRef(new Set<string>());
  const requestIdRef = useRef(0);
  // 페이지 이동 중에는 기존 검색어를 유지한다. 입력창 수정만으로 이전 목록을 바꾸지 않는다.
  const lastQueryRef = useRef("");
  const busyRef = useRef(false);
  const resultsRef = useRef<HTMLDivElement>(null);

  // 페이지 이동/여행 변경 이후 도착한 이전 검색 응답은 화면에 적용하지 않는다.
  useEffect(() => () => {
    requestIdRef.current += 1;
  }, []);

  /** 새 검색은 1페이지로, 페이지 이동은 직전 성공 검색어로 요청한다. */
  const loadPage = async (query: string, page: number, freshSearch: boolean) => {
    if (busyRef.current) return;
    busyRef.current = true;
    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError("");
    setLinkError("");
    if (freshSearch) setCatalog(null);
    try {
      const response = await myRealTripCatalogApi.searchTours(query, page);
      if (requestId !== requestIdRef.current) return;
      if (response.page !== page) {
        throw new Error("요청 페이지와 응답 페이지가 다릅니다.");
      }
      setCatalog(response);
      setSearchedKeyword(query);
      lastQueryRef.current = query;
      setPreparedLinks({});
      if (!freshSearch) resultsRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    } catch {
      if (requestId === requestIdRef.current) {
        setError("상품 페이지를 불러오지 못했어요. 제휴사 페이지 지원 여부와 서버 로그를 확인해주세요.");
      }
    } finally {
      busyRef.current = false;
      if (requestId === requestIdRef.current) setLoading(false);
    }
  };

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const query = keyword.trim();
    if (!query) return;
    void loadPage(query, 1, true);
  };

  const changePage = (page: number) => {
    if (!catalog || page === catalog.page || page < 1 || page > 200) return;
    if (!lastQueryRef.current) return;
    void loadPage(lastQueryRef.current, page, false);
  };

  // 팝업 차단을 피하려고 사용자 클릭 순간 새 창을 확보한다.
  // 차단된 환경에서는 결과 링크를 현재 카드에 보여주어 사용자가 직접 열 수 있게 한다.
  const handleOpenProduct = async (gid: string, productUrl: string) => {
    if (linkRequestRef.current.has(gid)) return;
    linkRequestRef.current.add(gid);
    setLinkLoadingGid(gid);
    setLinkError("");

    const popup = window.open("about:blank", "_blank");
    if (popup) {
      popup.opener = null;
      popup.document.title = "마이리얼트립 제휴 링크 연결 중";
      popup.document.body.textContent = "안전한 마이리얼트립 제휴 링크를 확인하고 있습니다.";
    }

    try {
      const responseUrl = await myRealTripMylinkApi.create(gid, productUrl);
      const link = safeMylinkUrl(responseUrl);
      if (!link) throw new Error("Invalid MyLink response URL");
      if (popup && !popup.closed) {
        popup.location.replace(link);
      } else {
        setPreparedLinks((current) => ({ ...current, [gid]: link }));
        setLinkError("새 창이 차단되었어요. 해당 상품 카드에서 준비된 제휴 링크를 눌러주세요.");
      }
    } catch {
      if (popup && !popup.closed) popup.close();
      setLinkError("제휴 링크를 만들지 못했어요. 잠시 후 다시 시도해주세요. 일반 상품 링크로 대체하지 않습니다.");
    } finally {
      linkRequestRef.current.delete(gid);
      setLinkLoadingGid(null);
    }
  };

  const visibleProducts = catalog?.items ?? [];
  // 카탈로그는 제공사의 페이지 하나만 표시하므로 중복 누적되지 않는다.
  const totalPages = catalog ? Math.min(200, Math.max(1, Math.ceil(catalog.totalCount / Math.max(catalog.perPage, 1)))) : 1;

  return (
    <section className="ar-stay" aria-labelledby="ar-stay-title">
      <div className="ar-stay-header">
        <div>
          <p className="ar-hub-eyebrow">
            <Ticket size={14} aria-hidden="true" />
            MYREALTRIP PARTNER PRODUCTS
          </p>
          <h2 id="ar-stay-title">실제 여행 상품 찾아보기</h2>
          <p className="ar-deals-description">
            {suggestedKeyword
              ? `선택한 여행에서 '${suggestedKeyword}' 지역을 제안했어요. 자유롭게 변경해 검색할 수 있어요.`
              : planName
                ? "여행 지역을 확정하지 못했어요. 실제 지역이나 활동명을 직접 입력해 주세요."
                : "여행 계획을 선택하지 않아도 지역이나 활동명으로 자유롭게 검색할 수 있어요."}
          </p>
        </div>
        <span className="ar-stay-tag">투어·티켓</span>
      </div>

      <form className="ar-deals-search" onSubmit={(event) => void handleSearch(event)}>
        <label htmlFor="ar-deals-keyword" className="ar-deals-label">
          지역·상품 검색
        </label>
        <div className="ar-deals-search-row">
          <input
            id="ar-deals-keyword"
            type="search"
            maxLength={80}
            autoComplete="off"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="예: 제주, 부산, 아쿠아리움"
          />
          <button type="submit" className="ar-button ar-button--primary" disabled={loading || !keyword.trim()}>
            {loading ? <Loader2 size={16} className="animate-spin" aria-hidden="true" /> : <Search size={16} aria-hidden="true" />}
            {loading ? "검색 중…" : "상품 검색"}
          </button>
        </div>
      </form>

      <p className="ar-stay-disclaimer">
        표시가는 검색 시점의 숙소 판매 표시가이며 1박 가격 또는 최종 결제 총액으로 확정할 수 없습니다.
        숙박 조건·객실 옵션·예약 가능 여부는 구매처에서 확인하세요. 0원은 무료가 아닌 가격 미확인으로 표시합니다.
        제휴 링크를 통해 확인하고 예약이 가능합니다.
      </p>

      {loading && <p className="ar-deals-status" role="status">마이리얼트립 상품을 찾고 있어요…</p>}
      {error && <p className="ar-deals-error" role="alert">{error}</p>}
      {linkError && <p className="ar-deals-error" role="alert">{linkError}</p>}
      {catalog && (
        <div ref={resultsRef} className="ar-deals-results">
          <p className="ar-deals-count" role="status">
            ‘{searchedKeyword}’ 검색 결과 {catalog.totalCount.toLocaleString("ko-KR")}개 ·
            {catalog.page}페이지 · 현재 {visibleProducts.length}개 표시
          </p>
          {catalog.items.length === 0 ? (
            <p className="ar-deals-status">검색된 상품이 없어요. 검색어를 바꿔 주세요.</p>
          ) : (
            <div className="ar-deals-grid">
              {visibleProducts.map((product) => {
                const imageUrl = safeImageUrl(product.imageUrl);
                const productUrl = safeProductUrl(product.productUrl, product.gid);
                return (
                  <article className="ar-deals-card" key={product.gid}>
                    <div className="ar-deals-image">
                      {imageUrl ? (
                        <img src={imageUrl} alt="" loading="lazy" />
                      ) : (
                        <span>이미지 준비 중</span>
                      )}
                    </div>
                    <div className="ar-deals-card-body">
                      <span className="ar-deals-category">{product.category || "여행 상품"}</span>
                      <h3>{product.itemName}</h3>
                      <strong className="ar-deals-price">
                        {product.priceDisplay ?? `${product.salePrice.toLocaleString("ko-KR")}원`}
                      </strong>
                      {product.reviewScore !== null && (
                        <p className="ar-deals-review">
                          <Star size={13} aria-hidden="true" />
                          {product.reviewScore.toFixed(1)}
                          {product.reviewCount !== null &&
                            ` · 후기 ${product.reviewCount.toLocaleString("ko-KR")}개`}
                        </p>
                      )}
                      {product.tags.length > 0 && (
                        <div className="ar-deals-tags">
                          {product.tags.map((tag) => <span key={tag}>{tag}</span>)}
                        </div>
                      )}
                      {productUrl ? (
                        <>
                          <button
                            type="button"
                            className="ar-deals-link"
                            disabled={linkLoadingGid === product.gid}
                            onClick={() => void handleOpenProduct(product.gid, productUrl)}
                          >
                            {linkLoadingGid === product.gid ? (
                              <><Loader2 size={14} className="animate-spin" aria-hidden="true" /> 링크 준비 중…</>
                            ) : (
                              <>제휴 링크로 상품 보기 <ArrowUpRight size={14} aria-hidden="true" /></>
                            )}
                          </button>
                          {preparedLinks[product.gid] && (
                            <a
                              href={preparedLinks[product.gid]}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="ar-deals-link ar-deals-link--ready"
                            >
                              제휴 링크 열기 <ArrowUpRight size={14} aria-hidden="true" />
                            </a>
                          )}
                        </>
                      ) : (
                        <span className="ar-deals-link ar-deals-link--disabled">상품 링크 확인 중</span>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
          <PartnerSearchPagination currentPage={catalog.page}
            totalPages={Math.min(totalPages, catalog.hasNextPage ? 200 : catalog.page)}
            loading={loading} onPageChange={changePage} />
          {catalog.totalCount > catalog.perPage * 200 && (
            <p className="ar-deals-disclaimer">제휴사 조회 부하 방지를 위해 앞의 200페이지까지만 이동할 수 있습니다.</p>
          )}
        </div>
      )}
    </section>
  );
}