// 로그인 이후 모든 화면(대시보드, 회원 관리, 퀘스트 관리 등)이 공유하는 뼈대.
// 왼쪽 고정 사이드바 + 오른쪽 콘텐츠 영역 구조.
import type { ReactNode } from "react"
import { Link, useLocation, useNavigate } from "react-router-dom"
import { clearAdminToken } from "../api/client"

const navItems = [
  { to: "/", label: "대시보드", icon: "◆" },
  { to: "/users", label: "회원 관리", icon: "◇" },
  { to: "/quests", label: "퀘스트 관리", icon: "◈" },
]

export default function AdminLayout({ children }: { children: ReactNode }) {
  const location = useLocation()
  const navigate = useNavigate()

  function handleLogout() {
    clearAdminToken()
    navigate("/login")
  }

  return (
      <div className="min-h-screen bg-slate-100 flex">
        {/* 사이드바 */}
        <aside className="w-60 shrink-0 bg-slate-900 text-slate-300 flex flex-col">
          <div className="h-16 flex items-center px-6 border-b border-slate-800">
          <span className="text-lg font-bold tracking-tight text-white">
            TripPamin <span className="text-indigo-400">Admin</span>
          </span>
          </div>

          <nav className="flex-1 px-3 py-5 space-y-1">
            {navItems.map((item) => {
              const active = location.pathname === item.to
              return (
                  <Link
                      key={item.to}
                      to={item.to}
                      className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                          active ? "bg-indigo-500/15 text-indigo-300" : "text-slate-400 hover:bg-slate-800 hover:text-slate-200"
                      }`}
                  >
                    <span className="text-xs">{item.icon}</span>
                    {item.label}
                  </Link>
              )
            })}
          </nav>

          <div className="p-3 border-t border-slate-800">
            <button
                onClick={handleLogout}
                className="w-full px-3 py-2.5 rounded-lg text-sm font-medium text-slate-400 hover:bg-slate-800 hover:text-red-300 transition-colors text-left"
            >
              로그아웃
            </button>
          </div>
        </aside>

        {/* 콘텐츠 영역 */}
        <div className="flex-1 flex flex-col min-w-0">
          <header className="h-16 bg-white border-b border-slate-200 flex items-center px-8">
            <p className="text-sm text-slate-500">관리자 콘솔</p>
          </header>
          <main className="flex-1 p-8">{children}</main>
        </div>
      </div>
  )
}
