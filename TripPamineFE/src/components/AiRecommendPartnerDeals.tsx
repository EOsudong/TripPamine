import { useEffect, useRef, useState, type FormEvent } from "react";
import { myRealTripMylinkApi } from "../api/myrealtripMylink";
import { ArrowUpRight, Loader2, Search, Ticket, Star } from "lucide-react";
import {
  myRealTripCatalogApi,
  type MyRealTripCatalogResponse,
} from "../api/myrealtripCatalog";
import "./AiRecommendPartnerDeals.css";

interface AiRecommendPartnerDealsProps {
  planName: string;
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
export default function AiRecommendPartnerDeals({ planName }: AiRecommendPartnerDealsProps) {
  const [keyword, setKeyword] = useState(planName);
  const [searchedKeyword, setSearchedKeyword] = useState("");
  const [catalog, setCatalog] = useState<MyRealTripCatalogResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [showAll, setShowAll] = useState(false);
  const [linkLoadingGid, setLinkLoadingGid] = useState<string | null>(null);
  const [linkError, setLinkError] = useState("");
  const [preparedLinks, setPreparedLinks] = useState<Record<string, string>>({});
  const linkRequestRef = useRef(new Set<string>());
  const requestIdRef = useRef(0);

  // 페이지 이동/여행 변경 이후 도착한 이전 검색 응답은 화면에 적용하지 않는다.
  useEffect(() => () => {
    requestIdRef.current += 1;
  }, []);

  const handleSearch = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const query = keyword.trim();
    if (!query || loading) return;

    const requestId = ++requestIdRef.current;
    setLoading(true);
    setError("");
    setCatalog(null);
    setSearchedKeyword(query);
    setShowAll(false);
    setLinkError("");
    setPreparedLinks({});
    try {
      const response = await myRealTripCatalogApi.searchTours(query);
      if (requestId === requestIdRef.current) setCatalog(response);
    } catch {
      if (requestId === requestIdRef.current)
        setError("상품을 불러오지 못했어요. 검색어를 확인하고 다시 시도해 주세요.");
    } finally {
      if (requestId === requestIdRef.current) setLoading(false);
    }
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

  const visibleProducts = catalog?.items.slice(0, showAll ? undefined : 6) ?? [];

  return (
    <section className="ar-deals tp-card-dark" aria-labelledby="ar-deals-heading">
      <div className="ar-deals-heading">
        <div>
          <p className="ar-eyebrow">
            <Ticket size={14} aria-hidden="true" />
            MYREALTRIP PARTNER PRODUCTS
          </p>
          <h2 id="ar-deals-heading">실제 여행 상품 찾아보기</h2>
          <p className="ar-deals-description">
            {planName
              ? "여행 정보를 기준으로 검색어를 제안했어요. 실제 지역이 맞는지 확인하고 검색해 주세요."
              : "여행 계획을 선택하지 않아도 지역이나 활동명으로 자유롭게 검색할 수 있어요."}
          </p>
        </div>
        <span className="ar-deals-provider">마이리얼트립</span>
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

      <p className="ar-deals-disclaimer">
        표시 가격은 검색 시점 기준입니다. 예약 가능 여부·인원별 최종 금액은 판매처에서 확인하세요.
        TripPamine에서 추가 할인이나 결제를 제공하는 것은 아닙니다.
        제휴 링크로 구매 시 TripPamine이 수수료를 받을 수 있습니다.
      </p>

      {loading && <p className="ar-deals-status" role="status">마이리얼트립 상품을 찾고 있어요…</p>}
      {error && <p className="ar-deals-error" role="alert">{error}</p>}
      {linkError && <p className="ar-deals-error" role="alert">{linkError}</p>}
      {catalog && (
        <>
          <p className="ar-deals-count" role="status">
            ‘{searchedKeyword}’ 검색 결과 {catalog.totalCount.toLocaleString("ko-KR")}개 ·
            현재 조회한 {catalog.items.length}개 중 {visibleProducts.length}개 표시
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
          {!showAll && catalog.items.length > 6 && (
            <button type="button" className="ar-deals-more" onClick={() => setShowAll(true)}>
              현재 조회된 나머지 {catalog.items.length - 6}개 상품 보기
            </button>
          )}
          {catalog.hasNextPage && (
            <p className="ar-deals-disclaimer">추가 검색 결과가 있지만 다음 페이지 조회는 아직 지원하지 않습니다.</p>
          )}
        </>
      )}
    </section>
  );
}
