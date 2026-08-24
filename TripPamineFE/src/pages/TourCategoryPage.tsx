// 국내 축제 및 행사 / 국내 관광 여행지 / 국내 관광 산업 3개 대분류가 공유하는 페이지.
// 라우트: /tour/:categoryKey (categoryKey = "festivals" | "destinations" | "industry")
// 사이드바 "정보" 그룹에서 대분류를 클릭하면 이 페이지로 이동하고, 상단 소분류 탭으로 필터링합니다.
import { useEffect, useState } from "react"
import { Link, useParams } from "react-router-dom"
import Header from "../components/Header"
import Sidebar from "../components/Sidebar"
import Footer from "../components/Footer"
import TourItemCard from "../components/TourItemCard"
import { findTourMainCategory } from "../data/tourCategories"
import { getTourItemsApi } from "../api/tour"
import type { TourItem } from "../types"

export default function TourCategoryPage() {
    const { categoryKey } = useParams<{ categoryKey: string }>()
    const category = findTourMainCategory(categoryKey)

    const [sidebarOpen, setSidebarOpen] = useState(false)
    const [activeSub, setActiveSub] = useState(category?.subCategories[0] ?? "전체")
    const [items, setItems] = useState<TourItem[]>([])
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    // 사이드바에서 다른 대분류를 눌러 categoryKey 자체가 바뀐 경우, 소분류 탭을 "전체"로 초기화
    useEffect(() => {
        setActiveSub(category?.subCategories[0] ?? "전체")
    }, [categoryKey]) // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        if (!category) return

        let cancelled = false
        setLoading(true)
        setError(null)

        getTourItemsApi(category.key, activeSub)
            .then((data) => {
                if (!cancelled) setItems(data)
            })
            .catch(() => {
                if (!cancelled) setError("관광정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.")
            })
            .finally(() => {
                if (!cancelled) setLoading(false)
            })

        return () => {
            cancelled = true
        }
    }, [category, activeSub])

    return (
        <div className="min-h-screen bg-slate-50">
            <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
            <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

            <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
                {!category ? (
                    <div className="max-w-3xl mx-auto px-4 py-24 text-center">
                        <p className="text-slate-400 mb-4">해당 카테고리를 찾을 수 없습니다.</p>
                        <Link to="/" className="text-sky-500 font-semibold text-sm">
                            홈으로 돌아가기 →
                        </Link>
                    </div>
                ) : (
                    <section className="py-14 px-4">
                        <div className="max-w-6xl mx-auto">
                            {/* Section header */}
                            <div className="mb-8">
                                <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-1">
                                    한국관광공사 오픈API
                                </p>
                                <h2 className="text-2xl md:text-3xl font-bold text-slate-900 flex items-center gap-2">
                                    <span>{category.icon}</span>
                                    {category.label}
                                </h2>
                                <p className="text-slate-500 text-sm mt-1">{category.description}</p>
                            </div>

                            {/* 소분류 필터 탭 */}
                            <div className="flex gap-2 flex-wrap mb-7">
                                {category.subCategories.map((sub) => (
                                    <button
                                        key={sub}
                                        onClick={() => setActiveSub(sub)}
                                        className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                                            activeSub === sub
                                                ? "bg-sky-500 text-white shadow-sm"
                                                : "bg-white text-slate-600 border border-slate-200 hover:border-sky-300 hover:text-sky-500"
                                        }`}
                                    >
                                        {sub}
                                    </button>
                                ))}
                            </div>

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
                                        <TourItemCard key={item.contentId} item={item} categoryKey={category.key} />
                                    ))}
                                </div>
                            )}
                        </div>
                    </section>
                )}

                <Footer />
            </main>
        </div>
    )
}

function LoadingGrid() {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="h-64 rounded-2xl bg-white border border-slate-100 animate-pulse" />
            ))}
        </div>
    )
}

function StateMessage({ label }: { label: string }) {
    return (
        <div className="text-center py-16 text-slate-400 text-sm bg-white rounded-2xl border border-dashed border-slate-200">
            {label}
        </div>
    )
}



