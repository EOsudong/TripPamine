// 여행지/축제 상세 페이지. 하나의 라우트(/detail/:type/:id)로 두 종류의 상세를 모두 처리합니다.
// - type이 "festival"이면 festivals 데이터에서, 그 외("destination")면 destinations 데이터에서 id로 찾음
// - 찾는 데이터가 없으면(잘못된 id 등) "정보를 찾을 수 없습니다" 안내 화면을 보여줌
import { useState } from "react"
import { Link, useParams } from "react-router-dom"
import Header from "../components/Header"
import Sidebar from "../components/Sidebar"
import Footer from "../components/Footer"
import { destinations } from "../data/destinations"
import { festivals } from "../data/festivals"
import type { Destination, Festival } from "../types"

export default function Detail() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const { type, id } = useParams<{ type: string; id: string }>() // URL의 /detail/:type/:id 부분을 그대로 읽어옴

  // type 값에 따라 어느 데이터 배열에서 찾을지 결정
  const item: Destination | Festival | undefined =
    type === "festival" ? festivals.find((f) => f.id === id) : destinations.find((d) => d.id === id)

  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

      <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
        {/* item이 없으면(못 찾으면) 안내 화면.
            있으면 "category" 필드 유무로 Festival/Destination을 구분해서 타입을 좁혀줌
            (festivals에만 있는 필드라서, 이 검사만으로 TypeScript가 안전하게 타입을 판단할 수 있음) */}
        {!item ? (
          <div className="max-w-3xl mx-auto px-4 py-24 text-center">
            <p className="text-slate-400 mb-4">해당 정보를 찾을 수 없습니다.</p>
            <Link to="/" className="text-sky-500 font-semibold text-sm">
              홈으로 돌아가기 →
            </Link>
          </div>
        ) : "category" in item ? (
          <FestivalDetail festival={item} />
        ) : (
          <DestinationDetail destination={item} />
        )}

        <Footer />
      </main>
    </div>
  )
}

// 여행지 상세 화면 (상단 큰 이미지 + 소개글 + AI 추천 정보 카드)
function DestinationDetail({ destination: d }: { destination: Destination }) {
  return (
    <div>
      <div className="relative h-72 sm:h-96 overflow-hidden">
        <img src={d.img} alt={d.name} className="w-full h-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
        <div className="absolute bottom-6 left-0 right-0 px-4 max-w-4xl mx-auto">
          <span className="inline-block px-2.5 py-1 bg-sky-500 text-white text-xs font-bold rounded-full mb-2">
            {d.tag}
          </span>
          <h1 className="text-3xl sm:text-4xl font-bold text-white drop-shadow-lg">{d.name}</h1>
          <p className="text-white/80 text-sm mt-1">{d.region}</p>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-10 grid grid-cols-1 sm:grid-cols-3 gap-8">
        <div className="sm:col-span-2">
          <h2 className="font-bold text-slate-800 text-lg mb-3">여행지 소개</h2>
          <p className="text-slate-600 text-sm leading-relaxed">{d.description}</p>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5 h-fit">
          <p className="text-xs text-slate-400 mb-1.5">AI 매칭 테마</p>
          <span className="inline-block px-2.5 py-1 bg-sky-50 text-sky-600 text-xs font-bold rounded-full mb-4">
            {d.theme}
          </span>
          <p className="text-xs text-slate-400 mb-1.5">AI 추천 이유</p>
          <p className="text-sm text-slate-700 leading-relaxed mb-4">{d.reason}</p>
          <button className="w-full py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-sm rounded-xl transition-colors shadow-sm shadow-sky-200">
            이 코스로 AI 플랜 만들기
          </button>
        </div>
      </div>
    </div>
  )
}

// 축제 상세 화면 (상단 큰 이미지 + 소개글/해시태그 + 행사 기간 카드)
function FestivalDetail({ festival: f }: { festival: Festival }) {
  return (
    <div>
      <div className="relative h-72 sm:h-96 overflow-hidden">
        <img src={f.img} alt={f.name} className="w-full h-full object-cover" />
        <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/10 to-transparent" />
        <div className="absolute bottom-6 left-0 right-0 px-4 max-w-4xl mx-auto">
          <span className="inline-block px-2.5 py-1 bg-sky-500 text-white text-xs font-bold rounded-full mb-2">
            {f.category}
          </span>
          <h1 className="text-3xl sm:text-4xl font-bold text-white drop-shadow-lg">{f.name}</h1>
          <p className="text-white/80 text-sm mt-1">{f.location}</p>
        </div>
      </div>

      <div className="max-w-4xl mx-auto px-4 py-10 grid grid-cols-1 sm:grid-cols-3 gap-8">
        <div className="sm:col-span-2">
          <h2 className="font-bold text-slate-800 text-lg mb-3">축제 소개</h2>
          <p className="text-slate-600 text-sm leading-relaxed mb-5">{f.description}</p>
          <div className="flex gap-1.5 flex-wrap">
            {f.tags.map((tag) => (
              <span key={tag} className="px-2.5 py-1 bg-sky-50 text-sky-600 text-xs font-semibold rounded-full">
                #{tag}
              </span>
            ))}
          </div>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 p-5 h-fit">
          <p className="text-xs text-slate-400 mb-1">행사 기간</p>
          <p className="font-bold text-slate-800 text-sm mb-4">
            {f.startDate} ~ {f.endDate}
          </p>
          <button className="w-full py-3 bg-sky-500 hover:bg-sky-600 text-white font-bold text-sm rounded-xl transition-colors shadow-sm shadow-sky-200">
            🔖 축제 저장하기
          </button>
        </div>
      </div>
    </div>
  )
}
