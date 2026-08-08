// 마이페이지 — 북마크(저장)한 여행지/축제를 모아 보여주는 페이지.
// 실제 로그인·서버 저장 기능은 아직 없어서, savedDestinationIds/savedFestivalIds에
// 하드코딩된 id로 "이미 저장되어 있다고 가정한" 더미 데이터를 보여줍니다.
// (실제 서비스에서는 로그인한 사용자의 북마크 목록을 서버에서 받아오면 됩니다)
import { useState } from "react"
import { Link } from "react-router-dom"
import Header from "../components/Header"
import Sidebar from "../components/Sidebar"
import Footer from "../components/Footer"
import { destinations } from "../data/destinations"
import { festivals } from "../data/festivals"

// 데모용 더미 북마크: 여행지 2개 + 축제 2개를 저장된 항목으로 가정
const savedDestinationIds = [destinations[0].id, destinations[3].id]
const savedFestivalIds = [festivals[0].id, festivals[1].id]

export default function MyPage() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [tab, setTab] = useState<"courses" | "festivals">("courses") // 현재 선택된 탭: "courses"(저장된 여행지) | "festivals"(저장된 축제)

  // 위 id 목록에 해당하는 실제 데이터만 골라냄
  const savedDestinations = destinations.filter((d) => savedDestinationIds.includes(d.id))
  const savedFestivals = festivals.filter((f) => savedFestivalIds.includes(f.id))

  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

      <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
        <div className="max-w-5xl mx-auto px-4 py-10">
          {/* 프로필 카드 */}
          <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 flex items-center gap-4 mb-8">
            <div className="w-16 h-16 rounded-full bg-gradient-to-br from-sky-500 to-sky-400 flex items-center justify-center shrink-0">
              <svg className="w-8 h-8 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                />
              </svg>
            </div>
            <div>
              <p className="font-bold text-slate-800 text-lg">여행자님, 안녕하세요 👋</p>
              <p className="text-sm text-slate-400">저장한 여행지와 축제를 한눈에 확인해보세요</p>
            </div>
          </div>

          {/* 탭 */}
          <div className="flex gap-2 mb-6">
            <button
              onClick={() => setTab("courses")}
              className={`px-5 py-2 rounded-full text-sm font-semibold transition-colors ${
                tab === "courses" ? "bg-sky-500 text-white shadow-sm" : "bg-white text-slate-600 border border-slate-200"
              }`}
            >
              🔖 저장된 여행지
            </button>
            <button
              onClick={() => setTab("festivals")}
              className={`px-5 py-2 rounded-full text-sm font-semibold transition-colors ${
                tab === "festivals" ? "bg-sky-500 text-white shadow-sm" : "bg-white text-slate-600 border border-slate-200"
              }`}
            >
              🎉 저장된 축제
            </button>
          </div>

          {/* 저장된 여행지 */}
          {tab === "courses" && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {savedDestinations.length === 0 && <EmptyState label="저장된 여행지가 없어요" />}
              {savedDestinations.map((d) => (
                <Link
                  key={d.id}
                  to={`/detail/destination/${d.id}`}
                  className="group bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-lg border border-slate-100 transition-all"
                >
                  <div className="relative h-36 overflow-hidden">
                    <img src={d.img} alt={d.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                  </div>
                  <div className="p-4">
                    <h3 className="font-bold text-slate-800 text-sm mb-1">{d.name}</h3>
                    <p className="text-xs text-slate-400">{d.region}</p>
                  </div>
                </Link>
              ))}
            </div>
          )}

          {/* 저장된 축제 */}
          {tab === "festivals" && (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {savedFestivals.length === 0 && <EmptyState label="저장된 축제가 없어요" />}
              {savedFestivals.map((f) => (
                <Link
                  key={f.id}
                  to={`/detail/festival/${f.id}`}
                  className="group bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-lg border border-slate-100 transition-all"
                >
                  <div className="relative h-36 overflow-hidden">
                    <img src={f.img} alt={f.name} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                  </div>
                  <div className="p-4">
                    <h3 className="font-bold text-slate-800 text-sm mb-1 line-clamp-1">{f.name}</h3>
                    <p className="text-xs text-slate-400">{f.location}</p>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>

        <Footer />
      </main>
    </div>
  )
}

// 저장된 항목이 하나도 없을 때 보여주는 빈 상태 안내 문구
function EmptyState({ label }: { label: string }) {
  return (
    <div className="col-span-full text-center py-16 text-slate-400 text-sm bg-white rounded-2xl border border-dashed border-slate-200">
      {label}
    </div>
  )
}
