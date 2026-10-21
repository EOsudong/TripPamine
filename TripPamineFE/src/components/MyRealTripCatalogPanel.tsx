import { useEffect, useState, type FormEvent } from "react";
import {
  myRealTripCatalogApi,
  type MyRealTripCatalogResponse,
} from "../api/myrealtripCatalog";

function safeHttpsUrl(value: string | null): string | undefined {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return url.protocol === "https:" ? url.href : undefined;
  } catch {
    return undefined;
  }
}

/** 검색된 제휴 상품 확인용 패널. 자동 구매/가상 할인/제휴 수익 링크 생성은 수행하지 않는다. */
export default function MyRealTripCatalogPanel() {
  const [keyword, setKeyword] = useState("제주");
  const [catalog, setCatalog] = useState<MyRealTripCatalogResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 언마운트 또는 연속 검색 시 이전 요청의 결과가 화면을 덮어쓰지 않도록 한다.
  const [requestVersion, setRequestVersion] = useState(0);

  useEffect(() => {
    if (requestVersion === 0) return;
    let cancelled = false;
    const run = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await myRealTripCatalogApi.searchTours(keyword);
        if (!cancelled) setCatalog(response);
      } catch {
        if (!cancelled) {
          setCatalog(null);
          setError("상품 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void run();
    return () => { cancelled = true; };
  }, [requestVersion]); // eslint-disable-line react-hooks/exhaustive-deps

  const onSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!keyword.trim() || loading) return;
    setRequestVersion((prev) => prev + 1);
  };

  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-5 text-slate-900 shadow-sm sm:p-7">
      <div className="mb-5">
        <p className="text-xs font-bold tracking-widest text-sky-600">TRIPPAMINE PARTNERS</p>
        <h2 className="mt-2 text-xl font-bold">여행 상품 찾기</h2>
        <p className="mt-1 text-sm text-slate-500">
          실제 등록된 투어·티켓 가격을 확인할 수 있습니다. 예약 가능 여부와 최종 가격은 판매처에서 확인해주세요.
        </p>
      </div>

      <form onSubmit={onSubmit} className="mb-5 flex flex-wrap gap-2">
        <label htmlFor="myrealtrip-keyword" className="sr-only">여행 지역 또는 상품 검색어</label>
        <input
          id="myrealtrip-keyword"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          maxLength={80}
          placeholder="예: 제주, 부산, 스노클링"
          className="min-w-0 flex-1 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-900 outline-none focus:border-sky-500"
        />
        <button
          type="submit"
          disabled={loading || !keyword.trim()}
          className="rounded-xl bg-sky-600 px-5 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
        >
          {loading ? "검색 중..." : "상품 검색"}
        </button>
      </form>

      {error && <p role="alert" className="mb-4 text-sm text-red-600">{error}</p>}
      {catalog && (
        <>
          <p className="mb-4 text-sm text-slate-500">
            검색 결과 {catalog.totalCount.toLocaleString()}개 중 현재 {catalog.items.length}개 표시
            {catalog.hasNextPage ? " · 추가 페이지 있음" : ""}
          </p>
          {catalog.items.length === 0 && (
            <p className="py-10 text-center text-sm text-slate-500">검색된 상품이 없습니다.</p>
          )}
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {catalog.items.map((product) => {
              const imageUrl = safeHttpsUrl(product.imageUrl);
              const productUrl = safeHttpsUrl(product.productUrl);
              return (
                <article key={product.gid} className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">
                  {imageUrl ? (
                    <img
                      src={imageUrl}
                      alt={product.itemName}
                      loading="lazy"
                      className="aspect-[16/10] w-full object-cover"
                    />
                  ) : (
                    <div className="flex aspect-[16/10] items-center justify-center bg-slate-100 text-xs text-slate-400">
                      이미지 준비 중
                    </div>
                  )}
                  <div className="space-y-2 p-4">
                    <p className="text-xs text-sky-600">{product.category ?? "여행 상품"}</p>
                    <h3 className="line-clamp-2 min-h-10 text-sm font-semibold">{product.itemName}</h3>
                    <p className="text-lg font-bold text-slate-900">
                      {product.priceDisplay ?? `${product.salePrice.toLocaleString()}원`}
                    </p>
                    {product.reviewScore != null && (
                      <p className="text-xs text-slate-500">
                        평점 {product.reviewScore.toFixed(1)}
                        {product.reviewCount != null ? ` · 리뷰 ${product.reviewCount.toLocaleString()}개` : ""}
                      </p>
                    )}
                    {product.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {product.tags.map((tag) => (
                          <span key={tag} className="rounded-full bg-sky-50 px-2 py-1 text-[11px] text-sky-700">
                            {tag}
                          </span>
                        ))}
                      </div>
                    )}
                    {productUrl ? (
                      <a
                        href={productUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="mt-2 block rounded-xl bg-slate-900 px-3 py-2.5 text-center text-sm font-semibold text-white"
                      >
                        마이리얼트립에서 상품 확인
                      </a>
                    ) : (
                      <span className="mt-2 block rounded-xl bg-slate-100 px-3 py-2.5 text-center text-sm text-slate-500">
                        상품 링크 확인 중
                      </span>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        </>
      )}
    </section>
  );
}
