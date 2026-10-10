// 국내 축제 및 행사 / 국내 관광 여행지 / 국내 관광 산업 3개 대분류가 공유하는 페이지.
// 라우트: /tour/:categoryKey (categoryKey = "festivals" | "destinations" | "industry")
// 사이드바 "정보" 그룹에서 대분류를 클릭하면 이 페이지로 이동하고, 상단 소분류 탭으로 필터링합니다.
import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import Footer from "../components/Footer";
import TourItemCard from "../components/TourItemCard";
import PageHero from "../components/PageHero";
import { findTourMainCategory } from "../data/tourCategories";
import { getTourItemsApi } from "../api/tour";
import type { TourItem } from "../types";

export default function TourCategoryPage() {
  const { categoryKey } = useParams<{ categoryKey: string }>();
  const category = findTourMainCategory(categoryKey);
  const [searchParams] = useSearchParams();
  const filterParam = searchParams.get("filter");

  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [activeSub, setActiveSub] = useState(
    filterParam && category?.subCategories.includes(filterParam)
      ? filterParam
      : (category?.subCategories[0] ?? "전체"),
  );
  const [items, setItems] = useState<TourItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // 사이드바에서 다른 대분류를 눌러 categoryKey 자체가 바뀐 경우, 소분류 탭을 "전체"로 초기화
  useEffect(() => {
    if (filterParam && category?.subCategories.includes(filterParam)) {
      setActiveSub(filterParam);
    } else {
      setActiveSub(category?.subCategories[0] ?? "전체");
    }
  }, [categoryKey, filterParam]);

  useEffect(() => {
    if (!category) return;

    let cancelled = false;
    setLoading(true);
    setError(null);

    getTourItemsApi(category.key, activeSub)
      .then((data) => {
        if (!cancelled) setItems(data);
      })
      .catch(() => {
        if (!cancelled)
          setError("관광정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [category, activeSub]);

  return (
    <div className="min-h-screen tp-aurora-light">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <Header
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => setSidebarOpen((v) => !v)}
      />

      <main
        className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}
      >
        {!category ? (
          <div className="max-w-3xl mx-auto px-4 py-24 text-center">
            <p className="text-slate-400 mb-4">
              해당 카테고리를 찾을 수 없습니다.
            </p>
            <Link to="/" className="text-sky-500 font-semibold text-sm">
              홈으로 돌아가기 →
            </Link>
          </div>
        ) : (
          <section className="px-4 py-8 md:py-10">
            <div className="max-w-6xl mx-auto">
              {/* Section header */}
              <PageHero
                className="mb-8"
                tone={category.key === "festivals" ? "pink" : category.key === "destinations" ? "cyan" : "violet"}
                eyebrow="한국관광공사 오픈API · AI CURATED"
                icon={category.icon}
                title={category.label}
                description={category.description}
              >
                {/* 소분류 필터 탭 */}
                <div className="flex flex-wrap gap-2">
                  {category.subCategories.map((sub) => (
                    <button
                      key={sub}
                      onClick={() => setActiveSub(sub)}
                      className={`rounded-full px-4 py-2 text-sm font-semibold transition ${
                        activeSub === sub
                          ? "bg-white text-night-950 shadow-[0_0_24px_rgba(167,139,250,0.55)]"
                          : "border border-white/15 bg-white/5 text-white/70 hover:bg-white/10 hover:text-white"
                      }`}
                    >
                      {sub}
                    </button>
                  ))}
                </div>
              </PageHero>

              {/* 목록 상태별 렌더링 */}
              {loading ? (
                <LoadingGrid />
              ) : error ? (
                <StateMessage label={error} />
              ) : items.length === 0 ? (
                <StateMessage label="조건에 맞는 결과가 없어요" />
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                  {items.map((item) => (
                    <TourItemCard
                      key={item.contentId}
                      item={item}
                      categoryKey={category.key}
                    />
                  ))}
                </div>
              )}
            </div>
          </section>
        )}

        <Footer />
      </main>
    </div>
  );
}

function LoadingGrid() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          className="h-64 animate-pulse rounded-3xl border border-white bg-gradient-to-br from-white to-sky-50"
        />
      ))}
    </div>
  );
}

function StateMessage({ label }: { label: string }) {
  return (
    <div className="rounded-3xl border border-dashed border-sky-200 bg-white/70 py-16 text-center text-sm text-slate-400 backdrop-blur">
      {label}
    </div>
  );
}
