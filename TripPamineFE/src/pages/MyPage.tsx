// 마이페이지 — 로그인한 사용자만 접근 가능 (router/Router.tsx의 ProtectedRoute가 보호함).
// 북마크(저장)한 관광정보를 "저장된 축제 및 행사 / 저장된 관광 여행지 / 저장된 관광 산업" 3개 탭으로
// 모아 보여주고, 가계부도 여기서 관리
//
// [닉네임] "여행자님" 고정 문구 대신 로그인한 사용자의 실제 닉네임(userName)을 /users/auth/me로 받아와 표시합니다.
//
// [계좌 기능 추가] 사용자가 계좌정보를 직접 입력해 등록/조회하는 ManualAccountSection을 추가함.
// [가계부 이동] 메인 페이지(Hero.tsx)에 있던 여행 가계부(AccountBook)를 이 페이지의 "가계부" 탭으로 옮김.
import { useEffect, useState } from "react";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import Footer from "../components/Footer";
import ManualAccountSection from "../components/ManualAccountSection";
import AccountBook from "../components/AccountBook";
import { getMyInfoApi } from "../api/auth";
import { getBookmarksApi, removeBookmarkApi } from "../api/bookmark";
import type { TourBookmark, TourMainCategoryKey } from "../types";
import { QuestMapView } from "../components/QuestMapView";
import { RealtimeQuestVerifier } from "../components/RealtimeQuestVerifier";
import { tap } from "node:test/reporters";

const FALLBACK_IMAGE =
  "https://images.unsplash.com/photo-1500835556837-99ac94a94552?w=900&h=600&fit=crop&auto=format";

// 북마크 탭 3개 — data/tourCategories.ts의 대분류(festivals/destinations/industry)와 동일한 키를 씀
const BOOKMARK_TABS: {
  key: TourMainCategoryKey;
  label: string;
  icon: string;
}[] = [
  { key: "festivals", label: "저장된 축제 및 행사", icon: "🎉" },
  { key: "destinations", label: "저장된 관광 여행지", icon: "🗺️" },
  { key: "industry", label: "저장된 관광 산업", icon: "🏨" },
];

type Tab = TourMainCategoryKey | "accountBook" | "quest";

function formatEventDate(yyyymmdd: string | null): string {
  if (!yyyymmdd || yyyymmdd.length !== 8) return "";
  return `${yyyymmdd.slice(0, 4)}.${yyyymmdd.slice(4, 6)}.${yyyymmdd.slice(6, 8)}`;
}

// 퀘스트 아이템 데이터 규격 (DB Quests & UserQuestLogs 매핑)
interface QuestItem {
  questId: number;
  questName: string;
  targetLat: number;
  targetLng: number;
  rewardPoint: number;
  description: string;
  status: "PROGRESS" | "SUCCESS" | "FAILED";
}

export default function MyPage() {
  const [searchParams] = useSearchParams();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [tab, setTab] = useState<Tab>("festivals");
  const navigate = useNavigate();

  // 사용자 프로필 정보 관리 상태
  const [userId, setUserId] = useState<number>(1); // 기본 가상 ID (AuthContext 연계 가능)
  const [userName, setUserName] = useState<string | null>(null);
  const [userGrade, setUserGrade] = useState<string>("Bronze");
  const [totalPoints, setTotalPoints] = useState<number>(0);

  // 북마크 상태
  const [bookmarks, setBookmarks] = useState<TourBookmark[]>([]);
  const [bookmarksLoading, setBookmarksLoading] = useState(true);
  const [bookmarksError, setBookmarksError] = useState<string | null>(null);

  // 탭 클릭 핸들러
  const handleTabClick = (tabKey: Tab, targetPath?: string) => {
    // 이미 활성화된 탭을 다시 누른 경우
    if (tab === tabKey) {
      if (targetPath) {
        navigate(targetPath); // 지정된 경로로 이동
      }
      return;
    }

    // 처음 누른 경우 탭 전환
    setTab(tabKey);
  };

  // 진행 및 완료 가능한 퀘스트 목록 더미 (실제 구현 시 API 연동)
  const [quests, setQuests] = useState<QuestItem[]>([
    {
      questId: 1,
      questName: "보령 머드 광장 현지 인증",
      targetLat: 36.311394,
      targetLng: 126.513364,
      rewardPoint: 1500,
      description:
        "머드 축제가 열리는 아름다운 대천해수욕장 광장 반경 50m 이내로 도달하세요!",
      status: "PROGRESS",
    },
    {
      questId: 2,
      questName: "여수 낭만포차 거리 정복 미션",
      targetLat: 34.739485,
      targetLng: 127.742394,
      rewardPoint: 2000,
      description:
        "밤바다가 수놓아지는 낭만포차 거리 하멜등대 주변에 도달하여 포인트 리워드를 받으세요.",
      status: "PROGRESS",
    },
    {
      questId: 3,
      questName: "제주 성산일출봉 하이킹 입구 도달",
      targetLat: 33.458923,
      targetLng: 126.942384,
      rewardPoint: 3000,
      description:
        "성산일출봉 매표소 진입 광장에 도착하면 GPS 검증을 수행하세요.",
      status: "SUCCESS",
    },
  ]);

  // 지도 시각화 대상을 나타내는 상태 (선택된 퀘스트)
  const [activeQuest, setActiveQuest] = useState<QuestItem | null>(null);

  // URL의 ?tab=... 값을 감지하여 해당 탭을 자동으로 선택해주는 Effect
  useEffect(() => {
    const tabParam = searchParams.get("tab") as Tab | null;
    if (
      tabParam &&
      [
        "festivals",
        "destinations",
        "industry",
        "accountBook",
        "quest",
      ].includes(tabParam)
    ) {
      setTab(tabParam);
    }
  }, [searchParams]);

  // 로그인한 사용자 닉네임 조회 (프로필 카드 인사말에 사용)
  useEffect(() => {
    getMyInfoApi()
      .then((info) => setUserName(info.userName))
      .catch((error) => {
        console.error("내 정보 조회 실패:", error);
        // 실패해도 화면은 기본 문구("여행자님")로 정상 동작하도록 조용히 넘어감
      });
  }, []);

  // 탭이 3개 북마크 카테고리 중 하나로 바뀔 때마다 해당 카테고리 북마크 목록을 새로 받아옴
  useEffect(() => {
    if (tab === "accountBook") return;

    let cancelled = false;
    setBookmarksLoading(true);
    setBookmarksError(null);

    getBookmarksApi(tab)
      .then((data) => {
        if (!cancelled) setBookmarks(data);
      })
      .catch((error) => {
        console.error("북마크 조회 실패:", error);
        if (!cancelled)
          setBookmarksError(
            "저장된 항목을 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
          );
      })
      .finally(() => {
        if (!cancelled) setBookmarksLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [tab]);

  const handleRemoveBookmark = async (contentId: string) => {
    // 낙관적 업데이트: 서버 응답 기다리지 않고 화면에서 먼저 지움 (실패하면 목록을 다시 불러와서 복구)
    const prev = bookmarks;
    setBookmarks((list) => list.filter((b) => b.contentId !== contentId));
    try {
      await removeBookmarkApi(contentId);
    } catch (error) {
      console.error("북마크 해제 실패:", error);
      alert("북마크 해제 중 오류가 발생했어요.");
      setBookmarks(prev);
    }
  };

  const activeBookmarkTab = BOOKMARK_TABS.find((t) => t.key === tab);

  // 퀘스트 완료 처리 성공 콜백
  const handleQuestSuccess = (questId: number, reward: number) => {
    // 1. 로컬 퀘스트 상태 성공으로 갱신
    setQuests((prev) =>
      prev.map((q) =>
        q.questId === questId ? { ...q, status: "SUCCESS" } : q,
      ),
    );
    // 2. 유저 포인트 즉각 누적 (도파민 즉각 자극)
    setTotalPoints((prev) => prev + reward);
    // 3. 현재 맵 활성화 상태 비활성
    setActiveQuest(null);
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <Header
        sidebarOpen={sidebarOpen}
        onToggleSidebar={() => setSidebarOpen((v) => !v)}
      />

      <main
        className={`transition-all duration-300 pt-16 ${
          sidebarOpen ? "lg:pl-64" : "lg:pl-16"
        }`}
      >
        <div className="max-w-5xl mx-auto px-4 py-10">
          {/* ================= 유저 프로필 및 스탯 카드 ================= */}
          <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 flex items-center justify-between gap-4 mb-8 flex-wrap">
            <div className="flex items-center gap-4">
              <div className="w-16 h-16 rounded-full bg-gradient-to-br from-sky-500 to-sky-400 flex items-center justify-center shrink-0 shadow-md">
                <svg
                  className="w-8 h-8 text-white"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                  />
                </svg>
              </div>
              <div>
                <h1 className="font-bold text-slate-800 text-lg flex items-center gap-1.5">
                  {userName || "여행자"}님, 안녕하세요 👋
                </h1>
                <p className="text-sm text-slate-400 mt-0.5">
                  오늘도 짜릿한 도파민 어드벤처를 떠날 준비가 되셨나요?
                </p>
              </div>
            </div>

            {/* RPG 스탯 정보 */}
            <div className="flex items-center gap-3">
              <div className="bg-slate-50 border border-slate-100 px-4 py-2.5 rounded-2xl text-center">
                <span className="block text-[11px] font-bold text-slate-400 uppercase">
                  RPG 등급
                </span>
                <span className="block text-sm font-black text-amber-500 mt-0.5">
                  🏆 {userGrade}
                </span>
              </div>
              <div className="bg-slate-50 border border-slate-100 px-4 py-2.5 rounded-2xl text-center">
                <span className="block text-[11px] font-bold text-slate-400 uppercase">
                  보유 포인트
                </span>
                <span className="block text-sm font-black text-sky-500 mt-0.5">
                  {totalPoints.toLocaleString()} P
                </span>
              </div>
            </div>
          </div>

          {/* ================= 탭 메뉴 컨트롤러 ================= */}
          <div className="flex gap-2 mb-6 flex-wrap">
            {/* 🎯 나의 퀘스트 */}
            <button
              onClick={() => handleTabClick("quest", "/quest-detail")} // 활성 상태에서 누르면 /quest-detail 로 이동
              className={`px-5 py-2 rounded-full text-sm font-semibold transition-colors ${
                tab === "quest"
                  ? "bg-sky-500 text-white shadow-sm"
                  : "bg-white text-slate-600 border border-slate-200"
              }`}
            >
              🎯 나의 퀘스트
            </button>

            {/* 💸 여행 가계부 */}
            <button
              onClick={() => setTab("accountBook")}
              className={`px-5 py-2 rounded-full text-sm font-semibold transition-colors ${
                tab === "accountBook"
                  ? "bg-sky-500 text-white shadow-sm"
                  : "bg-white text-slate-600 border border-slate-200 hover:bg-slate-50"
              }`}
            >
              💸 여행 가계부
            </button>

            {/* 저장된 북마크 탭들 */}
            {BOOKMARK_TABS.map((t) => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
                className={`px-5 py-2 rounded-full text-sm font-semibold transition-colors ${
                  tab === t.key
                    ? "bg-sky-500 text-white shadow-sm"
                    : "bg-white text-slate-600 border border-slate-200 hover:bg-slate-50"
                }`}
              >
                {t.icon} {t.label}
              </button>
            ))}
          </div>

          {/* ================= 탭 콘텐츠 분기 처리 ================= */}
          <section className="min-h-[400px]">
            {/* 1. 실시간 RPG 퀘스트 탭 */}
            {tab === "quest" && (
              <div className="space-y-6">
                {activeQuest ? (
                  /* 지도 검증 활성화 뷰 */
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <div className="lg:col-span-2 rounded-3xl overflow-hidden border border-slate-100 shadow-sm h-[450px] bg-white">
                      <QuestMapView
                        questId={activeQuest.questId}
                        questName={activeQuest.questName}
                        targetLat={activeQuest.targetLat}
                        targetLng={activeQuest.targetLng}
                        rewardPoint={activeQuest.rewardPoint}
                        userId={userId}
                        onSuccess={() =>
                          handleQuestSuccess(
                            activeQuest.questId,
                            activeQuest.rewardPoint,
                          )
                        }
                      />
                    </div>
                    <div className="flex flex-col justify-between rounded-3xl bg-white border border-slate-100 p-6 shadow-sm">
                      <div>
                        <span className="px-3 py-1 rounded-full text-xs font-bold bg-sky-50 text-sky-600 border border-sky-100">
                          진행 중인 미션
                        </span>
                        <h3 className="text-xl font-bold text-slate-800 mt-4">
                          {activeQuest.questName}
                        </h3>
                        <p className="text-slate-500 text-sm mt-2 leading-relaxed">
                          {activeQuest.description}
                        </p>

                        <div className="mt-6 space-y-1.5 text-xs text-slate-400 border-t border-slate-100 pt-4">
                          <p>
                            📍 목표 좌표: {activeQuest.targetLat.toFixed(5)},{" "}
                            {activeQuest.targetLng.toFixed(5)}
                          </p>
                          <p>📏 완료 인증 범위: 목표 지점 반경 50m 이내</p>
                        </div>
                      </div>

                      <div className="mt-6 space-y-2">
                        <RealtimeQuestVerifier
                          questId={activeQuest.questId}
                          questName={activeQuest.questName}
                          rewardPoint={activeQuest.rewardPoint}
                          userId={userId}
                        />
                        <button
                          onClick={() => setActiveQuest(null)}
                          className="w-full rounded-2xl bg-slate-100 hover:bg-slate-200 py-3 text-xs font-bold text-slate-600 transition"
                        >
                          지도 닫고 목록으로 이동
                        </button>
                      </div>
                    </div>
                  </div>
                ) : (
                  /* 퀘스트 목록 뷰 */
                  <div className="space-y-4">
                    <div className="flex items-center justify-between px-1">
                      <h2 className="text-base font-bold text-slate-800">
                        🗺️ 도전 가능한 실시간 필드 미션
                      </h2>
                      <span className="text-xs text-slate-400">
                        목적지에 도달하면 GPS 검증 버튼이 활성화됩니다
                      </span>
                    </div>

                    <div className="grid gap-4 md:grid-cols-2">
                      {quests.map((q) => (
                        <div
                          key={q.questId}
                          className={`rounded-2xl border p-5 transition flex flex-col justify-between bg-white shadow-sm ${
                            q.status === "SUCCESS"
                              ? "border-emerald-100 bg-emerald-50/20"
                              : "border-slate-100 hover:border-sky-200 hover:shadow-md"
                          }`}
                        >
                          <div>
                            <div className="flex items-start justify-between gap-2">
                              <h4 className="text-base font-bold text-slate-800 flex items-center gap-1.5">
                                {q.status === "SUCCESS" ? "✅" : "📌"}{" "}
                                {q.questName}
                              </h4>
                              <span
                                className={`text-xs px-2.5 py-0.5 rounded-full font-bold shrink-0 ${
                                  q.status === "SUCCESS"
                                    ? "bg-emerald-100 text-emerald-700"
                                    : "bg-sky-50 text-sky-600"
                                }`}
                              >
                                {q.status === "SUCCESS"
                                  ? "완료됨"
                                  : `+${q.rewardPoint} P`}
                              </span>
                            </div>
                            <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                              {q.description}
                            </p>
                          </div>

                          <div className="mt-5">
                            {q.status === "SUCCESS" ? (
                              <div className="w-full text-center py-2.5 rounded-xl bg-emerald-50 border border-emerald-100 text-emerald-600 text-xs font-bold">
                                🎉 {q.rewardPoint.toLocaleString()} P 적립 완료
                              </div>
                            ) : (
                              <button
                                onClick={() => setActiveQuest(q)}
                                className="w-full rounded-xl bg-sky-500 hover:bg-sky-600 text-white py-2.5 text-xs font-bold shadow-sm transition"
                              >
                                지도 확인 및 실시간 GPS 인증
                              </button>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* 2. 여행 가계부 및 수동 계좌 연동 탭 */}
            {tab === "accountBook" && (
              <div className="space-y-6">
                <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 md:p-8">
                  <h3 className="text-base font-bold text-slate-800 mb-4">
                    💳 내 계좌 정보
                  </h3>
                  <ManualAccountSection />
                </div>

                <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 md:p-8">
                  <h3 className="text-base font-bold text-slate-800 mb-6">
                    🧾 가계부 내역
                  </h3>
                  <AccountBook username={userName || "여행자"} />
                </div>
              </div>
            )}

            {/* 3. 저장된 북마크 탭들 */}
            {tab !== "accountBook" && tab !== "quest" && (
              <div>
                {bookmarksLoading ? (
                  <BookmarkGridSkeleton />
                ) : bookmarksError ? (
                  <EmptyState label={bookmarksError} />
                ) : bookmarks.length === 0 ? (
                  <EmptyState
                    label={`${
                      activeBookmarkTab?.label.replace("저장된 ", "") || "항목"
                    } 중 저장된 항목이 없어요`}
                  />
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
                    {bookmarks.map((b) => (
                      <BookmarkCard
                        key={b.contentId}
                        bookmark={b}
                        onRemove={() => handleRemoveBookmark(b.contentId)}
                      />
                    ))}
                  </div>
                )}
              </div>
            )}
          </section>
        </div>

        <Footer />
      </main>
    </div>
  );
}

// ================= 북마크 카드 =================
function BookmarkCard({
  bookmark: b,
  onRemove,
}: {
  bookmark: TourBookmark;
  onRemove: () => void;
}) {
  const statusConfig =
    b.status === "ongoing"
      ? { label: "진행중", bg: "bg-emerald-500" }
      : b.status === "upcoming"
        ? { label: "예정", bg: "bg-sky-500" }
        : null;

  return (
    <div className="group relative bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-lg border border-slate-100 transition-all duration-300">
      <button
        type="button"
        onClick={(e) => {
          e.preventDefault();
          onRemove();
        }}
        title="북마크 해제"
        className="absolute top-2 right-2 z-10 w-7 h-7 rounded-full bg-black/50 hover:bg-black/70 text-white text-xs flex items-center justify-center transition-colors"
      >
        ✕
      </button>

      <Link
        to={`/tour/${b.categoryKey}/${b.contentId}${
          b.contentTypeId ? `?contentTypeId=${b.contentTypeId}` : ""
        }`}
        className="block"
      >
        <div className="relative h-36 overflow-hidden bg-slate-100">
          <img
            src={b.imageUrl ?? FALLBACK_IMAGE}
            alt={b.title}
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
            onError={(e) => {
              e.currentTarget.src = FALLBACK_IMAGE;
            }}
          />
          {statusConfig && (
            <span
              className={`absolute top-2 left-2 px-2 py-0.5 ${statusConfig.bg} text-white text-[10px] font-bold rounded-full`}
            >
              {statusConfig.label}
            </span>
          )}
        </div>
        <div className="p-4">
          <h3 className="font-bold text-slate-800 text-sm mb-1 line-clamp-1">
            {b.title}
          </h3>
          <p className="text-xs text-slate-400 line-clamp-1">
            {b.address ?? b.category ?? ""}
          </p>
          {(b.eventStartDate || b.eventEndDate) && (
            <p className="text-[11px] text-slate-400 mt-1">
              {formatEventDate(b.eventStartDate)} ~{" "}
              {formatEventDate(b.eventEndDate)}
            </p>
          )}
        </div>
      </Link>
    </div>
  );
}

// 스켈레톤 로더
function BookmarkGridSkeleton() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
      {Array.from({ length: 3 }).map((_, i) => (
        <div
          key={i}
          className="h-56 rounded-2xl bg-white border border-slate-100 animate-pulse"
        />
      ))}
    </div>
  );
}

// 빈 상태 컴포넌트
function EmptyState({ label }: { label: string }) {
  return (
    <div className="col-span-full text-center py-16 text-slate-400 text-sm bg-white rounded-2xl border border-dashed border-slate-200">
      {label}
    </div>
  );
}
