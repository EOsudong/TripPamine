// 마이페이지 — 로그인한 사용자만 접근 가능 (router/Router.tsx의 ProtectedRoute가 보호함).
// 북마크(저장)한 관광정보를 "저장된 축제 및 행사 / 저장된 관광 여행지 / 저장된 관광 산업" 3개 탭으로
// 모아 보여주고, 가계부도 여기서 관리
//
// [닉네임] "여행자님" 고정 문구 대신 로그인한 사용자의 실제 닉네임(userName)을 /users/auth/me로 받아와 표시합니다.
//
// [계좌 기능 추가] 사용자가 계좌정보를 직접 입력해 등록/조회하는 ManualAccountSection을 추가함.
// [가계부 이동] 메인 페이지(Hero.tsx)에 있던 여행 가계부(AccountBook)를 이 페이지의 "가계부" 탭으로 옮김.
import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
import Header from "../components/Header"
import Sidebar from "../components/Sidebar"
import Footer from "../components/Footer"
import ManualAccountSection from "../components/ManualAccountSection"
import AccountBook from "../components/AccountBook"
import { getMyInfoApi } from "../api/auth"
import { getBookmarksApi, removeBookmarkApi } from "../api/bookmark"
import type { TourBookmark, TourMainCategoryKey } from "../types"

const FALLBACK_IMAGE =
    "https://images.unsplash.com/photo-1500835556837-99ac94a94552?w=900&h=600&fit=crop&auto=format"

// 북마크 탭 3개 — data/tourCategories.ts의 대분류(festivals/destinations/industry)와 동일한 키를 씀
const BOOKMARK_TABS: { key: TourMainCategoryKey; label: string; icon: string }[] = [
  { key: "festivals", label: "저장된 축제 및 행사", icon: "🎉" },
  { key: "destinations", label: "저장된 관광 여행지", icon: "🗺️" },
  { key: "industry", label: "저장된 관광 산업", icon: "🏨" },
]

type Tab = TourMainCategoryKey | "accountBook"

function formatEventDate(yyyymmdd: string | null): string {
  if (!yyyymmdd || yyyymmdd.length !== 8) return ""
  return `${yyyymmdd.slice(0, 4)}.${yyyymmdd.slice(4, 6)}.${yyyymmdd.slice(6, 8)}`
}

export default function MyPage() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [tab, setTab] = useState<Tab>("festivals")

  const [userName, setUserName] = useState<string | null>(null)

  const [bookmarks, setBookmarks] = useState<TourBookmark[]>([])
  const [bookmarksLoading, setBookmarksLoading] = useState(true)
  const [bookmarksError, setBookmarksError] = useState<string | null>(null)

  // 로그인한 사용자 닉네임 조회 (프로필 카드 인사말에 사용)
  useEffect(() => {
    getMyInfoApi()
        .then((info) => setUserName(info.userName))
        .catch((error) => {
          console.error("내 정보 조회 실패:", error)
          // 실패해도 화면은 기본 문구("여행자님")로 정상 동작하도록 조용히 넘어감
        })
  }, [])

  // 탭이 3개 북마크 카테고리 중 하나로 바뀔 때마다 해당 카테고리 북마크 목록을 새로 받아옴
  useEffect(() => {
    if (tab === "accountBook") return

    let cancelled = false
    setBookmarksLoading(true)
    setBookmarksError(null)

    getBookmarksApi(tab)
        .then((data) => {
          if (!cancelled) setBookmarks(data)
        })
        .catch((error) => {
          console.error("북마크 조회 실패:", error)
          if (!cancelled) setBookmarksError("저장된 항목을 불러오지 못했어요. 잠시 후 다시 시도해주세요.")
        })
        .finally(() => {
          if (!cancelled) setBookmarksLoading(false)
        })

    return () => {
      cancelled = true
    }
  }, [tab])

  const handleRemoveBookmark = async (contentId: string) => {
    // 낙관적 업데이트: 서버 응답 기다리지 않고 화면에서 먼저 지움 (실패하면 목록을 다시 불러와서 복구)
    const prev = bookmarks
    setBookmarks((list) => list.filter((b) => b.contentId !== contentId))
    try {
      await removeBookmarkApi(contentId)
    } catch (error) {
      console.error("북마크 해제 실패:", error)
      alert("북마크 해제 중 오류가 발생했어요.")
      setBookmarks(prev)
    }
  }

  const activeBookmarkTab = BOOKMARK_TABS.find((t) => t.key === tab)

  return (
      <div className="min-h-screen bg-slate-50">
        <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
        <Header sidebarOpen={sidebarOpen} onToggleSidebar={() => setSidebarOpen((v) => !v)} />

        <main className={`transition-all duration-300 pt-16 ${sidebarOpen ? "lg:pl-64" : "lg:pl-16"}`}>
          <div className="max-w-5xl mx-auto px-4 py-10">
            {/* 프로필 카드 */}
            <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 flex items-center justify-between gap-4 mb-8 flex-wrap">
              <div className="flex items-center gap-4">
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
                  <p className="font-bold text-slate-800 text-lg">{userName ?? "여행자"}님, 안녕하세요 👋</p>
                  <p className="text-sm text-slate-400">저장한 축제·여행지·관광정보를 한눈에 확인해보세요</p>
                </div>
              </div>
            </div>

            {/* 내 계좌 (직접 등록 + 목록) */}
            <ManualAccountSection />

            {/* 탭 */}
            <div className="flex gap-2 mb-6 flex-wrap">
              {BOOKMARK_TABS.map((t) => (
                  <button
                      key={t.key}
                      onClick={() => setTab(t.key)}
                      className={`px-5 py-2 rounded-full text-sm font-semibold transition-colors ${
                          tab === t.key ? "bg-sky-500 text-white shadow-sm" : "bg-white text-slate-600 border border-slate-200"
                      }`}
                  >
                    {t.icon} {t.label}
                  </button>
              ))}
              <button
                  onClick={() => setTab("accountBook")}
                  className={`px-5 py-2 rounded-full text-sm font-semibold transition-colors ${
                      tab === "accountBook" ? "bg-sky-500 text-white shadow-sm" : "bg-white text-slate-600 border border-slate-200"
                  }`}
              >
                🧾 가계부
              </button>
            </div>

            {/* 저장된 축제 및 행사 / 관광 여행지 / 관광 산업 (탭에 따라 categoryKey만 다름, 카드 구조는 동일) */}
            {activeBookmarkTab && (
                bookmarksLoading ? (
                    <BookmarkGridSkeleton />
                ) : bookmarksError ? (
                    <EmptyState label={bookmarksError} />
                ) : bookmarks.length === 0 ? (
                    <EmptyState label={`${activeBookmarkTab.label.replace("저장된 ", "")} 중 저장된 항목이 없어요`} />
                ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                      {bookmarks.map((b) => (
                          <BookmarkCard key={b.contentId} bookmark={b} onRemove={() => handleRemoveBookmark(b.contentId)} />
                      ))}
                    </div>
                )
            )}

            {/* 가계부 (여행 지출 기록 + 영수증 OCR) — 예전엔 메인 페이지 탭이었는데, 로그인 후에만
                볼 수 있는 마이페이지로 옮겨왔습니다 */}
            {tab === "accountBook" && (
                <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 md:p-8">
                  <AccountBook />
                </div>
            )}
          </div>

          <Footer />
        </main>
      </div>
  )
}

// 북마크 카드 한 장. TourItemCard.tsx와 거의 같은 구조지만, 저장 해제(✕) 버튼과
// Link 대신 감싸는 방식(버튼 클릭이 카드 이동과 겹치지 않도록)만 다름.
function BookmarkCard({ bookmark: b, onRemove }: { bookmark: TourBookmark; onRemove: () => void }) {
  const statusConfig =
      b.status === "ongoing"
          ? { label: "진행중", bg: "bg-emerald-500" }
          : b.status === "upcoming"
              ? { label: "예정", bg: "bg-sky-500" }
              : null

  return (
      <div className="group relative bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-lg border border-slate-100 transition-all">
        <button
            type="button"
            onClick={(e) => {
              e.preventDefault()
              onRemove()
            }}
            title="북마크 해제"
            className="absolute top-2 right-2 z-10 w-7 h-7 rounded-full bg-black/50 hover:bg-black/70 text-white text-xs flex items-center justify-center transition-colors"
        >
          ✕
        </button>

        <Link
            to={`/tour/${b.categoryKey}/${b.contentId}${b.contentTypeId ? `?contentTypeId=${b.contentTypeId}` : ""}`}
            className="block"
        >
          <div className="relative h-36 overflow-hidden bg-slate-100">
            <img
                src={b.imageUrl ?? FALLBACK_IMAGE}
                alt={b.title}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                onError={(e) => {
                  e.currentTarget.src = FALLBACK_IMAGE
                }}
            />
            {statusConfig && (
                <span className={`absolute top-2 left-2 px-2 py-0.5 ${statusConfig.bg} text-white text-[10px] font-bold rounded-full`}>
              {statusConfig.label}
            </span>
            )}
          </div>
          <div className="p-4">
            <h3 className="font-bold text-slate-800 text-sm mb-1 line-clamp-1">{b.title}</h3>
            <p className="text-xs text-slate-400 line-clamp-1">{b.address ?? b.category ?? ""}</p>
            {(b.eventStartDate || b.eventEndDate) && (
                <p className="text-[11px] text-slate-400 mt-1">
                  {formatEventDate(b.eventStartDate)} ~ {formatEventDate(b.eventEndDate)}
                </p>
            )}
          </div>
        </Link>
      </div>
  )
}

// 북마크 목록 로딩 중 보여주는 스켈레톤 그리드
function BookmarkGridSkeleton() {
  return (
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-56 rounded-2xl bg-white border border-slate-100 animate-pulse" />
        ))}
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
