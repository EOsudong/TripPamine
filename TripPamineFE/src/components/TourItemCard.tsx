// 한국관광공사 오픈API 데이터 카드 한 장을 그리는 공용 컴포넌트.
// festivals/destinations/industry 세 대분류가 전부 이 카드를 재사용합니다.
// festival 항목만 status/eventStartDate/eventEndDate가 채워져 있어서, 있을 때만 관련 UI를 보여줍니다.
// 카드를 클릭하면 /tour/:categoryKey/:contentId 상세 페이지로 이동합니다 (TourDetailPage.tsx).
import { Link } from "react-router-dom"
import type { TourItem, TourMainCategoryKey } from "../types"

const FALLBACK_IMAGE =
    "https://images.unsplash.com/photo-1500835556837-99ac94a94552?w=900&h=600&fit=crop&auto=format"

function formatEventDate(yyyymmdd: string | null): string {
    if (!yyyymmdd || yyyymmdd.length !== 8) return ""
    return `${yyyymmdd.slice(0, 4)}.${yyyymmdd.slice(4, 6)}.${yyyymmdd.slice(6, 8)}`
}

interface TourItemCardProps {
    item: TourItem
    categoryKey: TourMainCategoryKey
}

export default function TourItemCard({ item, categoryKey }: TourItemCardProps) {
    const statusConfig =
        item.status === "ongoing"
            ? { label: "진행중", bg: "bg-emerald-500", text: "text-white" }
            : item.status === "upcoming"
                ? { label: "예정", bg: "bg-sky-500", text: "text-white" }
                : null

    return (
        <Link
            to={`/tour/${categoryKey}/${item.contentId}?contentTypeId=${item.contentTypeId}`}
            className="group relative flex flex-col overflow-hidden rounded-3xl border border-white bg-white/90 shadow-[0_10px_30px_-20px_rgba(60,40,180,0.35)] backdrop-blur transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_30px_60px_-25px_rgba(80,60,200,0.45)]">
            <div className="relative m-2 h-44 shrink-0 overflow-hidden rounded-2xl bg-slate-100">
                <img
                    src={item.imageUrl ?? FALLBACK_IMAGE}
                    alt={item.title}
                    className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
                    onError={(e) => {
                        e.currentTarget.src = FALLBACK_IMAGE
                    }}
                />
                <div className="absolute inset-0 bg-gradient-to-t from-night-950/70 via-transparent to-transparent" />
                <span className="absolute right-3 top-3 flex h-8 w-8 translate-y-1 items-center justify-center rounded-full bg-white/90 text-sm text-night-950 opacity-0 shadow-lg transition group-hover:translate-y-0 group-hover:opacity-100">
                    ↗
                </span>

                {statusConfig && (
                    <span className={`absolute top-3 left-3 px-2.5 py-1 ${statusConfig.bg} ${statusConfig.text} text-xs font-bold rounded-full shadow-lg ring-2 ring-white/30`}>
            {statusConfig.label}
          </span>
                )}

                {item.address && (
                    <div className="absolute bottom-3 left-3 flex items-center gap-1">
                        <svg className="w-3.5 h-3.5 text-white/80" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"
                            />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                        </svg>
                        <span className="text-white/90 text-xs font-medium line-clamp-1">{item.address}</span>
                    </div>
                )}
            </div>

            <div className="flex flex-1 flex-col px-4 pb-4 pt-2">
                <div className="flex items-start justify-between gap-2 mb-2">
                    <h3 className="line-clamp-2 text-[15px] font-bold leading-snug text-slate-900 transition group-hover:text-sky-600">{item.title}</h3>
                    <span className="shrink-0 rounded-full bg-sky-50 px-2 py-0.5 text-[10px] font-bold text-sky-700">
            {item.category}
          </span>
                </div>

                {(item.eventStartDate || item.eventEndDate) && (
                    <p className="text-xs text-slate-400 flex items-center gap-1 mt-auto">
                        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                            />
                        </svg>
                        {formatEventDate(item.eventStartDate)} ~ {formatEventDate(item.eventEndDate)}
                    </p>
                )}
            </div>
        </Link>
    )
}
