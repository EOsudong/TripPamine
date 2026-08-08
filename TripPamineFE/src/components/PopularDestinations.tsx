// 인기 국내 여행지 섹션.
// 가격/평점 대신 "AI 매칭 테마"와 "AI 추천 이유"를 보여줘서 AI 추천 플랫폼다운 느낌을 주도록 구성.
import { useState } from "react"
import type { MouseEvent } from "react"
import { Link } from "react-router-dom"
import { destinations } from "../data/destinations"
import { categories } from "../data/categories"

export default function PopularDestinations() {
  const [activeCategory, setActiveCategory] = useState("전체") // 상단 카테고리 필터("전체"/"바다"/"산·자연" 등)
  const [liked, setLiked] = useState<string[]>([]) // 하트(찜) 누른 여행지 id 목록 (데모용 — 새로고침하면 초기화됨)

  // 하트 버튼 클릭 시 찜 상태 토글
  function toggleLike(id: string) {
    setLiked((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  // 선택된 카테고리와 destination.theme 값이 일치하는 것만 필터링 ("전체"면 전부 표시)
  const filtered = destinations.filter((d) => activeCategory === "전체" || d.theme === activeCategory)

  return (
    <section id="destinations" className="py-14 px-4 bg-slate-50">
      <div className="max-w-6xl mx-auto">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between mb-8 gap-4">
          <div>
            <p className="text-sky-500 text-xs font-semibold tracking-widest uppercase mb-1">Top Picks</p>
            <h2 className="text-2xl md:text-3xl font-bold text-slate-900">인기 국내 여행지</h2>
          </div>
          <div className="flex gap-2 flex-wrap">
            {categories.map((c) => (
              <button
                key={c}
                onClick={() => setActiveCategory(c)}
                className={`px-4 py-1.5 rounded-full text-sm font-medium transition-colors ${
                  activeCategory === c
                    ? "bg-sky-500 text-white shadow-sm"
                    : "bg-white text-slate-600 border border-slate-200 hover:border-sky-300 hover:text-sky-500"
                }`}
              >
                {c}
              </button>
            ))}
          </div>
        </div>

        {/* 여행지 카드 목록.
            카드 전체를 하나의 Link로 감싸지 않고, "이미지+제목 영역"과
            "AI 코스 만들기 버튼" 두 곳에 각각 다른 이동 동작을 줘야 해서
            바깥은 일반 div로, 안쪽에 필요한 곳만 Link/a로 감싸는 구조입니다 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {filtered.map((d) => (
            <div
              key={d.id}
              className="group rounded-2xl overflow-hidden bg-white shadow-sm hover:shadow-xl border border-slate-100 transition-all duration-300 flex flex-col"
            >
              {/* 이미지 영역: 클릭하면 상세 페이지(/detail/destination/:id)로 이동 */}
              <Link to={`/detail/destination/${d.id}`} className="relative overflow-hidden h-48 block">
                <img
                  src={d.img}
                  alt={d.name}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
                <span className="absolute top-3 left-3 px-2.5 py-1 bg-sky-500 text-white text-xs font-bold rounded-full">
                  {d.tag}
                </span>
                {/* 찜(하트) 버튼: 부모가 Link라서, 클릭이 상세 페이지 이동으로 이어지지 않도록 preventDefault 처리 */}
                <button
                  onClick={(e: MouseEvent) => {
                    e.preventDefault()
                    toggleLike(d.id)
                  }}
                  className="absolute top-3 right-3 w-8 h-8 bg-white/80 hover:bg-white rounded-full flex items-center justify-center transition-colors"
                >
                  <svg
                    className={`w-4 h-4 transition-colors ${liked.includes(d.id) ? "text-red-500" : "text-slate-400"}`}
                    fill={liked.includes(d.id) ? "currentColor" : "none"}
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"
                    />
                  </svg>
                </button>
              </Link>

              <div className="p-4 flex flex-col flex-1">
                <Link to={`/detail/destination/${d.id}`} className="block mb-1.5">
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="font-bold text-slate-800 text-base">{d.name}</h3>
                    <span className="shrink-0 px-2 py-0.5 bg-sky-50 text-sky-600 text-[10px] font-bold rounded-full mt-0.5">
                      {d.theme}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400">{d.region}</p>
                </Link>

                {/* AI 추천 이유 */}
                <p className="text-xs text-slate-500 leading-relaxed flex items-start gap-1.5 mt-1">
                  <svg className="w-3.5 h-3.5 text-sky-500 shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  {d.reason}
                </p>

                {/* "AI 코스 만들기" 버튼: 페이지 이동이 아니라 같은 홈 화면 안에서
                    id="ai-planner" 섹션(Hero)으로 스크롤만 이동시키는 앵커 링크 */}
                <a
                  href="#ai-planner"
                  className="mt-3 w-full py-2 rounded-xl bg-sky-50 hover:bg-sky-100 text-sky-600 text-xs font-bold text-center transition-colors"
                >
                  🤖 이 여행지로 AI 코스 만들기
                </a>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
