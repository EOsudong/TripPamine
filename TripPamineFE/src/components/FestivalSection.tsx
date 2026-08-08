// 전국 축제 & 행사 섹션 (기존 특가 패키지 섹션을 대체).
// 필터 탭으로 진행중/예정/카테고리별 축제를 걸러 보여주고, 카드 클릭 시 상세 페이지로 이동합니다.
import { useState } from "react"
import type { MouseEvent } from "react"
import { Link } from "react-router-dom"
import { festivals } from "../data/festivals"
import { festivalFilterTabs } from "../data/categories"
import type { Festival } from "../types"

export default function FestivalSection() {
  const [activeFilter, setActiveFilter] = useState("전체") // 현재 선택된 필터 탭
  const [bookmarked, setBookmarked] = useState<string[]>([]) // 북마크한 축제 id 목록 (데모용 — 새로고침하면 초기화됨)

  // 북마크 버튼 클릭 시: 이미 북마크했으면 제거, 아니면 추가 (토글)
  function toggleBookmark(id: string) {
    setBookmarked((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  // 선택된 필터 탭에 맞게 festivals 배열을 걸러냄
  // "진행 중"/"예정"은 status 값으로, 나머지 탭은 category 값으로 매칭
  const filtered = festivals.filter((f) => {
    if (activeFilter === "전체") return true
    if (activeFilter === "진행 중") return f.status === "ongoing"
    if (activeFilter === "예정") return f.status === "upcoming"
    return f.category === activeFilter
  })

  return (
    <section id="festivals" className="py-14 px-4">
      <div className="max-w-6xl mx-auto">
        {/* Section header */}
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
          <div>
            <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-1">Festivals &amp; Events</p>
            <h2 className="text-2xl md:text-3xl font-bold text-slate-900 flex items-center gap-2">
              전국 축제 &amp; 행사
              <span className="px-2 py-0.5 bg-sky-100 text-sky-600 text-xs font-bold rounded-full">
                {festivals.filter((f) => f.status === "ongoing").length} 진행 중
              </span>
            </h2>
            <p className="text-slate-500 text-sm mt-1">현재 진행 중이거나 예정된 전국 주요 축제를 확인하세요</p>
          </div>
          <button className="shrink-0 text-sm font-medium text-sky-500 hover:text-sky-600 flex items-center gap-1 transition-colors">
            전체보기
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>

        {/* Filter tabs */}
        <div className="flex gap-2 flex-wrap mb-7">
          {festivalFilterTabs.map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveFilter(tab)}
              className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                activeFilter === tab
                  ? "bg-sky-500 text-white shadow-sm"
                  : "bg-white text-slate-600 border border-slate-200 hover:border-sky-300 hover:text-sky-500"
              }`}
            >
              {tab}
            </button>
          ))}
        </div>

        {/* 축제 카드 목록 — 카드 하나하나는 아래 FestivalCard 컴포넌트가 그려줌 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {filtered.map((festival) => (
            <FestivalCard
              key={festival.id}
              festival={festival}
              bookmarked={bookmarked.includes(festival.id)}
              onBookmark={() => toggleBookmark(festival.id)}
            />
          ))}
        </div>
      </div>
    </section>
  )
}

// 축제 카드 하나를 그리는 컴포넌트.
// festival.status 값("ongoing"/"upcoming"/"ended")에 따라 뱃지 문구·색상이 달라짐
interface FestivalCardProps {
  festival: Festival
  bookmarked: boolean
  onBookmark: () => void
}

function FestivalCard({ festival, bookmarked, onBookmark }: FestivalCardProps) {
  const statusConfig: Record<Festival["status"], { label: string; bg: string; text: string }> = {
    ongoing: { label: "D·DAY", bg: "bg-emerald-500", text: "text-white" },
    upcoming: { label: `D-${festival.dday}`, bg: "bg-sky-500", text: "text-white" },
    ended: { label: "종료", bg: "bg-slate-300", text: "text-slate-600" },
  }
  const s = statusConfig[festival.status]

  return (
    <Link
      to={`/detail/festival/${festival.id}`}
      className="group bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-xl border border-slate-100 transition-all duration-300 cursor-pointer flex flex-col"
    >
      {/* Image */}
      <div className="relative overflow-hidden h-44 shrink-0">
        <img
          src={festival.img}
          alt={festival.name}
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent" />

        {/* D-Day badge */}
        <span className={`absolute top-3 left-3 px-2.5 py-1 ${s.bg} ${s.text} text-xs font-bold rounded-full`}>
          {s.label}
        </span>

        {/* 북마크 버튼: 카드 전체가 Link(상세 페이지 이동)라서,
            버튼 클릭이 상세 페이지 이동으로 이어지지 않도록 preventDefault/stopPropagation 처리 */}
        <button
          onClick={(e: MouseEvent) => {
            e.preventDefault()
            e.stopPropagation()
            onBookmark()
          }}
          className="absolute top-3 right-3 w-8 h-8 bg-white/80 hover:bg-white rounded-full flex items-center justify-center transition-colors"
        >
          {bookmarked ? (
            <svg className="w-4 h-4 text-sky-500" fill="currentColor" viewBox="0 0 24 24">
              <path d="M5 4v16l7-3 7 3V4H5z" />
            </svg>
          ) : (
            <svg className="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 4v16l7-3 7 3V4H5z" />
            </svg>
          )}
        </button>

        {/* Location overlay */}
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
          <span className="text-white/90 text-xs font-medium">{festival.location}</span>
        </div>
      </div>

      {/* Content */}
      <div className="p-4 flex flex-col flex-1">
        <div className="flex items-start justify-between gap-2 mb-2">
          <h3 className="font-bold text-slate-800 text-sm leading-snug line-clamp-2">{festival.name}</h3>
          <span className="shrink-0 px-2 py-0.5 bg-slate-100 text-slate-500 text-[10px] font-semibold rounded-full">
            {festival.category}
          </span>
        </div>

        {/* Date */}
        <p className="text-xs text-slate-400 flex items-center gap-1 mb-3">
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
            />
          </svg>
          {festival.startDate} ~ {festival.endDate}
        </p>

        {/* Tags */}
        <div className="flex gap-1.5 mt-auto flex-wrap">
          {festival.tags.map((tag) => (
            <span key={tag} className="px-2 py-0.5 bg-sky-50 text-sky-600 text-[10px] font-semibold rounded-full">
              #{tag}
            </span>
          ))}
        </div>
      </div>
    </Link>
  )
}
