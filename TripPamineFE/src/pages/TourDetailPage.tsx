// 국내 축제 및 행사 / 국내 관광 여행지 / 국내 관광 산업 카드를 클릭하면 이동하는 상세 페이지.
// 라우트: /tour/:categoryKey/:contentId (TourItemCard.tsx에서 카드를 클릭하면 여기로 옴)
// 한국관광공사 오픈API(detailCommon2)를 백엔드가 실시간으로 호출해서 개요/전화번호/홈페이지 등을 내려줍니다.
import { useEffect, useState } from "react"
import { Link, useParams, useSearchParams } from "react-router-dom"
import Header from "../components/Header"
import Sidebar from "../components/Sidebar"
import Footer from "../components/Footer"
import { findTourMainCategory } from "../data/tourCategories"
import { getTourDetailApi } from "../api/tour"
import type { TourDetail } from "../types"

const FALLBACK_IMAGE =
    "https://images.unsplash.com/photo-1500835556837-99ac94a94552?w=1200&h=800&fit=crop&auto=format"

function formatEventDate(yyyymmdd: string | null): string {
    if (!yyyymmdd || yyyymmdd.length !== 8) return ""
    return `${yyyymmdd.slice(0, 4)}.${yyyymmdd.slice(4, 6)}.${yyyymmdd.slice(6, 8)}`
}

export default function TourDetailPage() {
    const { categoryKey, contentId } = useParams<{ categoryKey: string; contentId: string }>()
    const [searchParams] = useSearchParams()
    // 카드 클릭 시점에 이미 알고 있던 contentTypeId를 쿼리로 넘겨받음 (백엔드가 캐시에서
    // 항목을 못 찾았을 때만 보조로 사용하는 값이라 없어도 상세 조회 자체는 정상 동작함)
    const contentTypeId = searchParams.get("contentTypeId")
    const category = findTourMainCategory(categoryKey)

    const [sidebarOpen, setSidebarOpen] = useState(false)
    const [detail, setDetail] = useState<TourDetail | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        if (!contentId) return

        let cancelled = false
        setLoading(true)
        setError(null)

        getTourDetailApi(contentId, contentTypeId)
            .then((data) => {
                if (!cancelled) setDetail(data)
            })
            .catch(() => {
                if (!cancelled) setError("상세 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.")
            })
            .finally(() => {
                if (!cancelled) setLoading(false)
            })

        return () => {
            cancelled = true
        }
    }, [contentId, contentTypeId])

    const statusConfig =
        detail?.status === "ongoing"
            ? { label: "진행중", bg: "bg-emerald-500" }
            : detail?.status === "upcoming"
                ? { label: "예정", bg: "bg-sky-500" }
                : null

    const backTo = category ? category.path : "/"
    const backLabel = category ? `${category.label} 목록으로` : "홈으로"

    return (
        <div className="min-h-screen bg-slate-50">
            <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
            <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

            <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
                {loading ? (
                    <div className="max-w-4xl mx-auto px-4 py-10">
                        <div className="h-72 sm:h-96 rounded-3xl bg-white border border-slate-100 animate-pulse mb-6" />
                        <div className="h-5 w-1/3 bg-white border border-slate-100 rounded animate-pulse" />
                    </div>
                ) : error || !detail ? (
                    <div className="max-w-3xl mx-auto px-4 py-24 text-center">
                        <p className="text-slate-400 mb-4">{error ?? "해당 정보를 찾을 수 없습니다."}</p>
                        <Link to={backTo} className="text-sky-500 font-semibold text-sm">
                            {backLabel} →
                        </Link>
                    </div>
                ) : (
                    <div>
                        {/* 상단 대표 이미지 + 제목 */}
                        <div className="relative h-72 sm:h-96 overflow-hidden">
                            <img
                                src={detail.imageUrl ?? FALLBACK_IMAGE}
                                alt={detail.title}
                                className="w-full h-full object-cover"
                                onError={(e) => {
                                    e.currentTarget.src = FALLBACK_IMAGE
                                }}
                            />
                            <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
                            <div className="absolute bottom-6 left-0 right-0 px-4 max-w-4xl mx-auto">
                                <div className="flex items-center gap-2 mb-2">
                                    {detail.category && (
                                        <span className="inline-block px-2.5 py-1 bg-sky-500 text-white text-xs font-bold rounded-full">
                                            {detail.category}
                                        </span>
                                    )}
                                    {statusConfig && (
                                        <span className={`inline-block px-2.5 py-1 ${statusConfig.bg} text-white text-xs font-bold rounded-full`}>
                                            {statusConfig.label}
                                        </span>
                                    )}
                                </div>
                                <h1 className="text-3xl sm:text-4xl font-bold text-white drop-shadow-lg">{detail.title}</h1>
                                {detail.address && <p className="text-white/80 text-sm mt-1">{detail.address}</p>}
                            </div>
                        </div>

                        {/* 소개 + 정보 카드 */}
                        <div className="max-w-4xl mx-auto px-4 py-10 grid grid-cols-1 sm:grid-cols-3 gap-8">
                            <div className="sm:col-span-2">
                                <h2 className="font-bold text-slate-800 text-lg mb-3">소개</h2>
                                <p className="text-slate-600 text-sm leading-relaxed whitespace-pre-line">
                                    {detail.overview || "제공되는 소개 정보가 없어요."}
                                </p>
                            </div>

                            <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5 h-fit space-y-4">
                                {(detail.eventStartDate || detail.eventEndDate) && (
                                    <div>
                                        <p className="text-xs text-slate-400 mb-1">행사 기간</p>
                                        <p className="font-bold text-slate-800 text-sm">
                                            {formatEventDate(detail.eventStartDate)} ~ {formatEventDate(detail.eventEndDate)}
                                        </p>
                                    </div>
                                )}

                                {detail.address && (
                                    <div>
                                        <p className="text-xs text-slate-400 mb-1">주소</p>
                                        <p className="text-sm text-slate-700">{detail.address}</p>
                                    </div>
                                )}

                                {detail.tel && (
                                    <div>
                                        <p className="text-xs text-slate-400 mb-1">문의</p>
                                        <p className="text-sm text-slate-700">{detail.tel}</p>
                                    </div>
                                )}

                                {detail.homepage && (
                                    <div>
                                        <p className="text-xs text-slate-400 mb-1">홈페이지</p>
                                        <a
                                            href={detail.homepage}
                                            target="_blank"
                                            rel="noreferrer"
                                            className="text-sm text-sky-500 font-semibold break-all hover:underline"
                                        >
                                            {detail.homepage}
                                        </a>
                                    </div>
                                )}

                                <Link
                                    to={backTo}
                                    className="block w-full text-center py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-sm rounded-xl transition-colors shadow-sm shadow-sky-200"
                                >
                                    {backLabel}
                                </Link>
                            </div>
                        </div>
                    </div>
                )}

                <Footer />
            </main>
        </div>
    )
}
