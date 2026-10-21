import { useEffect, useRef, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { ArrowUpRight, Loader2, Search, Star, Ticket, X } from "lucide-react";
import { myRealTripCatalogApi, type MyRealTripCatalogResponse } from "../api/myrealtripCatalog";
import { myRealTripMylinkApi } from "../api/myrealtripMylink";
import "./NegoPartnerDealsDialog.css";

interface NegoPartnerDealsDialogProps {
  offerName: string;
  onClose: () => void;
}

// AiNego의 기존 제안명에는 지역을 보장하는 별도 필드가 없다.
// 마지막 임시 상품명 접미사만 제거하고, 검색 실행은 사용자가 직접 결정한다.
function suggestKeyword(offerName: string): string {
  return offerName.replace(/\s*막판 특가 패키지\s*$/, "").trim().slice(0, 80);
}

function verifiedProductUrl(value: string | null, gid: string): string | null {
  if (!value || !/^[0-9]{1,20}$/.test(gid)) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" &&
      url.hostname === "experiences.myrealtrip.com" &&
      url.pathname === `/products/${gid}` &&
      !url.search && !url.hash && !url.port && !url.username && !url.password
      ? url.href : null;
  } catch {
    return null;
  }
}

function verifiedAffiliateUrl(value: string): string | null {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname === "myrealt.rip" &&
      !url.port && !url.username && !url.password && !url.hash
      ? url.href : null;
  } catch {
    return null;
  }
}

function verifiedImageUrl(value: string | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" ? url.href : null;
  } catch {
    return null;
  }
}

/** 실제 제휴 검색 결과를 별도 표시한다. 기존 AiNego 금액을 상품 가격으로 사용하지 않는다. */
export default function NegoPartnerDealsDialog({
  offerName,
  onClose,
}: NegoPartnerDealsDialogProps) {
  const [keyword, setKeyword] = useState(() => suggestKeyword(offerName));
  const [searchedKeyword, setSearchedKeyword] = useState("");
  const [catalog, setCatalog] = useState<MyRealTripCatalogResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [linkLoadingGid, setLinkLoadingGid] = useState<string | null>(null);
  const [linkError, setLinkError] = useState("");
  const [preparedLinks, setPreparedLinks] = useState<Record<string, string>>({});
  const searchSequenceRef = useRef(0);
  const openingLinksRef = useRef(new Set<string>());
  const inputRef = useRef<HTMLInputElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    const previousFocus = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    inputRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCloseRef.current();
      }
      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
      ));
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      searchSequenceRef.current += 1;
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleKeyDown);
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus({ preventScroll: true });
      }
    };
  }, []);

  const search = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const query = keyword.trim();
    if (!query || loading) return;
    const sequence = ++searchSequenceRef.current;
    setLoading(true);
    setError("");
    setLinkError("");
    setCatalog(null);
    setPreparedLinks({});
    setSearchedKeyword(query);
    try {
      const result = await myRealTripCatalogApi.searchTours(query);
      if (sequence === searchSequenceRef.current) setCatalog(result);
    } catch {
      if (sequence === searchSequenceRef.current) {
        setError("실제 상품 검색에 실패했어요. 검색어를 바꿔 다시 시도해주세요.");
      }
    } finally {
      if (sequence === searchSequenceRef.current) setLoading(false);
    }
  };

  const openAffiliate = async (gid: string, productUrl: string) => {
    if (openingLinksRef.current.has(gid)) return;
    openingLinksRef.current.add(gid);
    setLinkLoadingGid(gid);
    setLinkError("");
    // 실제 클릭 이벤트 안에서 팝업을 미리 열어 비동기 요청 후 차단되는 것을 방지한다.
    const popup = window.open("about:blank", "_blank");
    if (popup) {
      popup.opener = null;
      popup.document.title = "제휴 링크 연결 중";
      popup.document.body.textContent = "안전한 제휴 링크를 확인하고 있습니다.";
    }
    try {
      const rawLink = await myRealTripMylinkApi.create(gid, productUrl);
      const link = verifiedAffiliateUrl(rawLink);
      if (!link) throw new Error("Invalid MyLink URL");
      if (popup && !popup.closed) {
        popup.location.replace(link);
      } else {
        setPreparedLinks((current) => ({ ...current, [gid]: link }));
        setLinkError("새 창이 차단되었어요. 해당 카드의 제휴 링크 열기를 눌러주세요.");
      }
    } catch {
      if (popup && !popup.closed) popup.close();
      setLinkError("MyLink 제휴 링크를 발급받지 못했습니다. 일반 상품 링크로 자동 대체하지 않습니다.");
    } finally {
      openingLinksRef.current.delete(gid);
      setLinkLoadingGid(null);
    }
  };

  return createPortal(
    <div
      className="nego-partner-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        ref={dialogRef}
        className="nego-partner-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="nego-partner-title"
        aria-describedby="nego-partner-description"
      >
        <header className="nego-partner-header">
          <div>
            <p className="nego-partner-kicker"><Ticket size={14} /> MYREALTRIP PARTNER</p>
            <h2 id="nego-partner-title">실제 제휴 상품 비교</h2>
            <p id="nego-partner-description">
              여행명을 검색어로 제안했어요. 지역·체험명으로 변경해 직접 검색해 주세요.
            </p>
          </div>
          <button type="button" className="nego-partner-close" onClick={onClose} aria-label="상품 검색 닫기">
            <X size={20} />
          </button>
        </header>

        <form className="nego-partner-search" onSubmit={(event) => void search(event)}>
          <label htmlFor="nego-partner-keyword">여행 지역·상품 이름</label>
          <div className="nego-partner-search-row">
            <input
              ref={inputRef}
              id="nego-partner-keyword"
              type="search"
              maxLength={80}
              autoComplete="off"
              placeholder="예: 제주, 부산, 아쿠아리움"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
            <button type="submit" disabled={!keyword.trim() || loading}>
              {loading ? <Loader2 size={16} className="animate-spin" /> : <Search size={16} />}
              {loading ? "검색 중…" : "상품 검색"}
            </button>
          </div>
        </form>
        <p className="nego-partner-disclaimer">
          표시 금액은 검색 시점의 판매 가격입니다. 기존 할인과는
          관련이 없으며 추가 할인은 제공하지 않습니다. 구매 및 최종 금액·인원·예약 가능 여부는
          판매처에서 확인해 주세요.
        </p>

        {loading && <p className="nego-partner-message" role="status">상품을 조회하고 있습니다…</p>}
        {error && <p className="nego-partner-message nego-partner-error" role="alert">{error}</p>}
        {linkError && <p className="nego-partner-message nego-partner-error" role="alert">{linkError}</p>}

        {catalog && (
          <>
            <p className="nego-partner-count" role="status">
              ‘{searchedKeyword}’ 총 {catalog.totalCount.toLocaleString("ko-KR")}개 검색 결과 ·
              첫 조회 {catalog.items.length}개 중 최대 6개 표시
            </p>
            {catalog.items.length === 0 ? (
              <p className="nego-partner-message">검색된 상품이 없습니다. 다른 검색어로 시도해 주세요.</p>
            ) : (
              <div className="nego-partner-grid">
                {catalog.items.slice(0, 6).map((product) => {
                  const url = verifiedProductUrl(product.productUrl, product.gid);
                  const image = verifiedImageUrl(product.imageUrl);
                  return (
                    <article className="nego-partner-card" key={product.gid}>
                      <div className="nego-partner-image">
                        {image ? <img alt="" src={image} loading="lazy" /> : <span>이미지 준비 중</span>}
                      </div>
                      <div className="nego-partner-card-body">
                        <span className="nego-partner-category">{product.category ?? "여행 상품"}</span>
                        <h3>{product.itemName}</h3>
                        <strong>{product.priceDisplay ?? `${product.salePrice.toLocaleString("ko-KR")}원`}</strong>
                        {product.reviewScore !== null && (
                          <p className="nego-partner-review">
                            <Star size={14} /> {product.reviewScore.toFixed(1)}
                            {product.reviewCount !== null ? ` · 후기 ${product.reviewCount.toLocaleString("ko-KR")}개` : ""}
                          </p>
                        )}
                        {product.tags.length > 0 && (
                          <p className="nego-partner-tags">{product.tags.slice(0, 3).join(" · ")}</p>
                        )}
                        {url ? (
                          <>
                            <button
                              type="button"
                              className="nego-partner-link"
                              disabled={linkLoadingGid === product.gid}
                              onClick={() => void openAffiliate(product.gid, url)}
                            >
                              {linkLoadingGid === product.gid ? "링크 준비 중…" : "제휴 상품 보기"}
                              <ArrowUpRight size={15} />
                            </button>
                            {preparedLinks[product.gid] && (
                              <a
                                className="nego-partner-link nego-partner-link-ready"
                                href={preparedLinks[product.gid]}
                                target="_blank"
                                rel="noopener noreferrer"
                              >
                                제휴 링크 열기 <ArrowUpRight size={15} />
                              </a>
                            )}
                          </>
                        ) : (
                          <span className="nego-partner-no-link">상품 주소 확인 중</span>
                        )}
                      </div>
                    </article>
                  );
                })}
              </div>
            )}
            {catalog.hasNextPage && (
              <p className="nego-partner-disclaimer">
                추가 검색 결과가 있습니다. 다음 페이지 요청은 아직 준비중 입니다.
              </p>
            )}
          </>
        )}
      </div>
    </div>,
    document.body,
  );
}
