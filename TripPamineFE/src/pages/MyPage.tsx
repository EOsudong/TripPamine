// 마이페이지 — 로그인한 사용자만 접근 가능 (router/Router.tsx의 ProtectedRoute가 보호함).
// 북마크(저장)한 관광정보를 "저장된 축제 및 행사 / 저장된 관광 여행지 / 저장된 관광 산업" 3개 탭으로
// 모아 보여주고, 가계부도 여기서 관리
//
// [닉네임] "여행자님" 고정 문구 대신 로그인한 사용자의 실제 닉네임(userName)을 /users/auth/me로 받아와 표시합니다.
//
// [계좌 기능 추가] 사용자가 계좌정보를 직접 입력해 등록/조회하는 ManualAccountSection을 추가함.
// [가계부 이동] 메인 페이지(Hero.tsx)에 있던 여행 가계부(AccountBook)를 이 페이지의 "가계부" 탭으로 옮김.
// [퀘스트 화면 분리] 퀘스트 기능은 /quests 전용 화면에서 관리합니다.
// [금융상품 북마크 추가] "저장된 금융상품" 탭 — /finance 페이지에서 🔖로 저장한 정기예금/적금을 모아 보여줍니다.
import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import ManualAccountSection from "../components/ManualAccountSection";
import AppLayout from "../layout/AppLayout";
import AccountBook from "../components/AccountBook";
import { getMyInfoApi } from "../api/auth";
import { questApi, type RpgStatsResponse } from "../api/quest";
import NicknameEditModal from "../components/NicknameEditModal";
import TitleSelectModal from "../components/TitleSelectModal";
import { getBookmarksApi, removeBookmarkApi } from "../api/bookmark";
import { getFinanceBookmarksApi, removeFinanceBookmarkApi } from "../api/financeBookmark";
import type { FinancialProductBookmark, TourBookmark, TourMainCategoryKey } from "../types";
import { useAuth } from "../context/AuthContext";

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

// 금융상품 북마크 탭 — /finance 페이지의 🔖 버튼으로 저장한 항목
const FINANCE_TAB = { key: "finance" as const, label: "저장된 금융상품", icon: "🏦" };

type Tab = TourMainCategoryKey | "finance" | "accountBook";

function formatEventDate(yyyymmdd: string | null): string {
  if (!yyyymmdd || yyyymmdd.length !== 8) return "";
  return `${yyyymmdd.slice(0, 4)}.${yyyymmdd.slice(4, 6)}.${yyyymmdd.slice(6, 8)}`;
}

function formatRate(rate: number | null): string {
  if (rate === null || rate === undefined) return "-";
  return `${rate.toFixed(2)}%`;
}

export default function MyPage() {
  const { userId } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  // 마이페이지 초기 화면 여행 가계부로 설정
  const [tab, setTab] = useState<Tab>("accountBook");

  // 사용자 프로필 정보 관리 상태 - 전부 /users/auth/me 응답을 그대로 신뢰
  const [userName, setUserName] = useState<string | null>(null);
  const [userGrade, setUserGrade] = useState<string>("Bronze");
  const [totalPoints, setTotalPoints] = useState<number>(0);

  // [닉네임·칭호 변경] 현재 칭호(RPG 스탯)와 모달 열림 상태
  const [rpgStats, setRpgStats] = useState<RpgStatsResponse | null>(null);
  const [profileModal, setProfileModal] = useState<"nickname" | "title" | null>(null);

  // 관광정보 북마크 상태
  const [bookmarks, setBookmarks] = useState<TourBookmark[]>([]);
  const [bookmarksLoading, setBookmarksLoading] = useState(true);
  const [bookmarksError, setBookmarksError] = useState<string | null>(null);

  // 금융상품 북마크 상태
  const [financeBookmarks, setFinanceBookmarks] = useState<FinancialProductBookmark[]>([]);
  const [financeBookmarksLoading, setFinanceBookmarksLoading] = useState(true);
  const [financeBookmarksError, setFinanceBookmarksError] = useState<string | null>(null);

  // URL의 ?tab=... 값을 감지하여 해당 탭을 자동으로 선택해주는 Effect
  useEffect(() => {
    const tabParam = searchParams.get("tab");

    if (tabParam === "quest") {
      navigate("/quests", { replace: true });
      return;
    }

    if (
        tabParam &&
        ["festivals", "destinations", "industry", "finance", "accountBook"].includes(
            tabParam,
        )
    ) {
      setTab(tabParam as Tab);
    }
  }, [navigate, searchParams]);

  // 로그인한 사용자 정보 조회 (닉네임/등급/포인트)
  useEffect(() => {
    getMyInfoApi()
        .then((info) => {
          setUserName(info.userName);
          setUserGrade(info.grade);
          setTotalPoints(info.totalPoints);
        })
        .catch((error) => {
          console.error("내 정보 조회 실패:", error);
          // 실패해도 화면은 기본 문구("여행자님")로 정상 동작하도록 조용히 넘어감
        });

    // 현재 장착 칭호 표시용. 실패해도 칭호 배지만 안 보이고 나머지는 정상 동작.
    questApi
        .getMyStats()
        .then(setRpgStats)
        .catch((error) => console.error("RPG 스탯 조회 실패:", error));
  }, []);

  // 탭이 바뀔 때마다 해당 탭에 맞는 북마크 목록을 새로 받아옴
  // (관광정보 3개 탭은 기존 /bookmark API, "금융상품" 탭은 financeBookmark API로 분기)
  useEffect(() => {
    if (tab === "accountBook") return;

    let cancelled = false;

    if (tab === "finance") {
      setFinanceBookmarksLoading(true);
      setFinanceBookmarksError(null);

      getFinanceBookmarksApi()
          .then((data) => {
            if (!cancelled) setFinanceBookmarks(data);
          })
          .catch((error) => {
            console.error("금융상품 북마크 조회 실패:", error);
            if (!cancelled)
              setFinanceBookmarksError(
                  "저장된 금융상품을 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
              );
          })
          .finally(() => {
            if (!cancelled) setFinanceBookmarksLoading(false);
          });

      return () => {
        cancelled = true;
      };
    }

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

  const handleRemoveFinanceBookmark = async (bookmarkId: string) => {
    const prev = financeBookmarks;
    setFinanceBookmarks((list) => list.filter((b) => b.bookmarkId !== bookmarkId));
    try {
      await removeFinanceBookmarkApi(bookmarkId);
    } catch (error) {
      console.error("금융상품 북마크 해제 실패:", error);
      alert("북마크 해제 중 오류가 발생했어요.");
      setFinanceBookmarks(prev);
    }
  };

  const activeBookmarkTab = BOOKMARK_TABS.find((t) => t.key === tab);

  // 왼쪽 메뉴 구성 (가계부 → 관광 북마크 → 금융 북마크)
  const menuGroups: {
    title: string;
    items: { key: Tab; label: string; icon: string; desc: string }[];
  }[] = [
    {
      title: "MONEY",
      items: [
        { key: "accountBook", label: "가계부 · 계좌", icon: "💸", desc: "수입·지출 기록과 연동 계좌" },
        { key: FINANCE_TAB.key, label: "저장한 금융상품", icon: FINANCE_TAB.icon, desc: "관심 예금 · 적금" },
      ],
    },
    {
      title: "SAVED PLACES",
      items: BOOKMARK_TABS.map((t) => ({
        key: t.key,
        label: t.label.replace("저장된 ", ""),
        icon: t.icon,
        desc: "북마크한 관광 정보",
      })),
    },
  ];

  const allMenuItems = menuGroups.flatMap((g) => g.items);
  const activeMenu = allMenuItems.find((m) => m.key === tab) ?? allMenuItems[0];

  const quickLinks = [
    { label: "자산 대시보드", icon: "📊", to: "/assets" },
    { label: "AI 코스 추천", icon: "✨", to: "/ai-recommend" },
    { label: "퀘스트", icon: "🏆", to: "/quests" },
    { label: "금융상품 탐색", icon: "🏦", to: "/finance" },
  ];

  return (
      <AppLayout
          pageClassName="tp-aurora-light"
      >
        <div className="mx-auto max-w-6xl px-4 py-8 md:py-10">

          {/* ================= 프로필 히어로 ================= */}
          <section className="relative isolate mb-6 overflow-hidden rounded-[2rem] bg-night-950 p-6 text-white shadow-[0_40px_80px_-50px_rgba(60,40,180,0.8)] md:p-8">
            <div className="absolute inset-0 -z-10 tp-aurora" />
            <div className="absolute inset-0 -z-10 tp-dot-grid opacity-40 [mask-image:linear-gradient(90deg,transparent,black_50%)]" />

            <div className="flex flex-col gap-8 lg:flex-row lg:items-center lg:justify-between">
              <div className="flex items-center gap-5">
                <div className="relative">
                  <span className="absolute -inset-1.5 rounded-[1.4rem] bg-gradient-to-br from-violet-500 via-cyan-400 to-pink-500 opacity-70 blur-md" />
                  <div className="relative flex h-[72px] w-[72px] items-center justify-center rounded-[1.25rem] tp-gradient-bg font-display text-3xl font-bold">
                    {(userName || "여").slice(0, 1)}
                  </div>
                </div>

                <div>
                  <p className="text-[10px] font-black tracking-[0.22em] text-cyan-300">MY SPACE</p>
                  {rpgStats?.currentTitleName && (
                      <span className="mt-1.5 inline-flex items-center gap-1 rounded-full border border-amber-300/40 bg-amber-300/10 px-2.5 py-0.5 text-xs font-bold text-amber-300">
                        🎖️ {rpgStats.currentTitleName}
                      </span>
                  )}
                  <h1 className="mt-1.5 font-display text-2xl font-bold tracking-tight md:text-3xl">
                    {userName || "여행자"}님의 공간
                  </h1>
                  <p className="mt-1.5 text-sm text-white/55">
                    AI가 여행 · 자산 · 활동 기록을 함께 보고 있어요.
                  </p>
                  {/* [닉네임·칭호 변경] */}
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button
                        type="button"
                        onClick={() => setProfileModal("nickname")}
                        className="rounded-full border border-white/15 bg-white/[0.06] px-3 py-1.5 text-xs font-bold text-white/80 transition hover:bg-white/15"
                    >
                      ✏️ 닉네임 변경
                    </button>
                    <button
                        type="button"
                        onClick={() => setProfileModal("title")}
                        className="rounded-full border border-white/15 bg-white/[0.06] px-3 py-1.5 text-xs font-bold text-white/80 transition hover:bg-white/15"
                    >
                      🎖️ 칭호 변경
                    </button>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 sm:flex">
                <div className="rounded-2xl border border-white/10 bg-white/[0.06] px-5 py-3.5 backdrop-blur">
                  <span className="block text-[10px] font-black uppercase tracking-[0.16em] text-white/45">등급</span>
                  <span className="mt-1 block font-display text-xl font-bold text-amber-300">🏆 {userGrade}</span>
                </div>
                <div className="rounded-2xl border border-white/10 bg-white/[0.06] px-5 py-3.5 backdrop-blur">
                  <span className="block text-[10px] font-black uppercase tracking-[0.16em] text-white/45">포인트</span>
                  <span className="mt-1 block font-display text-xl font-bold text-cyan-300">{totalPoints.toLocaleString()} P</span>
                </div>
              </div>
            </div>

            {/* 빠른 이동 */}
            <div className="mt-8 grid grid-cols-2 gap-2 border-t border-white/10 pt-6 sm:grid-cols-4">
              {quickLinks.map((link) => (
                  <button
                      key={link.to}
                      type="button"
                      onClick={() => navigate(link.to)}
                      className="group flex items-center justify-between rounded-2xl bg-white/[0.04] px-4 py-3 text-left text-sm font-semibold text-white/80 transition hover:bg-white/10 hover:text-white"
                  >
                    <span className="flex items-center gap-2">
                      <span>{link.icon}</span>
                      {link.label}
                    </span>
                    <span className="text-white/30 transition group-hover:translate-x-0.5 group-hover:text-white">→</span>
                  </button>
              ))}
            </div>
          </section>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">

            {/* ================= 왼쪽 메뉴 ================= */}
            <aside className="lg:sticky lg:top-24 lg:self-start">
              <nav className="tp-card flex gap-2 overflow-x-auto p-2 lg:flex-col lg:gap-0 lg:p-3">
                {menuGroups.map((group) => (
                    <div key={group.title} className="flex gap-2 lg:mb-3 lg:block lg:last:mb-0">
                      <p className="hidden px-3 pb-2 pt-1 text-[10px] font-black tracking-[0.18em] text-slate-400 lg:block">
                        {group.title}
                      </p>
                      {group.items.map((item) => {
                        const active = tab === item.key;
                        return (
                            <button
                                key={item.key}
                                type="button"
                                onClick={() => setTab(item.key)}
                                className={`flex shrink-0 items-center gap-3 rounded-2xl px-3 py-2.5 text-left text-sm transition lg:w-full ${
                                    active
                                        ? "bg-night-950 text-white shadow-[0_12px_24px_-14px_rgba(30,20,90,0.9)]"
                                        : "text-slate-600 hover:bg-white hover:text-slate-900"
                                }`}
                            >
                            <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-xl text-base ${active ? "bg-white/10" : "bg-slate-100"}`}>
                              {item.icon}
                            </span>
                              <span className="font-semibold">{item.label}</span>
                            </button>
                        );
                      })}
                    </div>
                ))}
              </nav>
            </aside>

            {/* ================= 탭 콘텐츠 ================= */}
            <section className="min-h-[400px] min-w-0">
              <div className="mb-5 flex items-end justify-between gap-3">
                <div>
                  <p className="tp-kicker">{activeMenu.desc}</p>
                  <h2 className="tp-heading mt-1 flex items-center gap-2 text-2xl">
                    <span>{activeMenu.icon}</span>
                    {activeMenu.label}
                  </h2>
                </div>
                {tab === "accountBook" && (
                    <button
                        type="button"
                        onClick={() => navigate("/assets")}
                        className="tp-btn-ghost hidden sm:inline-flex"
                    >
                      📊 자산 리포트 보기
                    </button>
                )}
              </div>

              {/* 1. 가계부 및 계좌 */}
              {tab === "accountBook" && (
                  <div className="space-y-6">
                    <div className="tp-card p-6 md:p-7">
                      <div className="mb-5 flex items-center gap-3">
                        <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-emerald-50 text-lg">💳</span>
                        <div>
                          <h3 className="font-bold text-slate-900">내 계좌</h3>
                          <p className="text-xs text-slate-400">연동한 계좌의 잔액이 가계부와 함께 반영돼요</p>
                        </div>
                      </div>
                      <ManualAccountSection />
                    </div>

                    <div className="tp-card p-6 md:p-7">
                      <div className="mb-6 flex items-center gap-3">
                        <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-sky-50 text-lg">🧾</span>
                        <div>
                          <h3 className="font-bold text-slate-900">가계부</h3>
                          <p className="text-xs text-slate-400">직접 입력하거나 영수증을 찍으면 AI가 내역을 채워요</p>
                        </div>
                      </div>
                      <AccountBook
                          key={userId ?? "signed-out"}
                          userId={userId}
                      />
                    </div>
                  </div>
              )}

              {/* 2. 저장된 관광정보 북마크 탭들 */}
              {(tab === "festivals" || tab === "destinations" || tab === "industry") && (
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
                        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
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

              {/* 3. 저장된 금융상품 탭 */}
              {tab === "finance" && (
                  <div>
                    {financeBookmarksLoading ? (
                        <BookmarkGridSkeleton />
                    ) : financeBookmarksError ? (
                        <EmptyState label={financeBookmarksError} />
                    ) : financeBookmarks.length === 0 ? (
                        <EmptyState label="저장된 금융상품이 없어요" />
                    ) : (
                        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
                          {financeBookmarks.map((b) => (
                              <FinanceBookmarkCard
                                  key={b.bookmarkId}
                                  bookmark={b}
                                  onRemove={() => handleRemoveFinanceBookmark(b.bookmarkId)}
                              />
                          ))}
                        </div>
                    )}
                  </div>
              )}
            </section>
          </div>
        </div>
        {profileModal === "nickname" && (
            <NicknameEditModal
                currentUserName={userName}
                onClose={() => setProfileModal(null)}
                onSaved={(info) => {
                  setUserName(info.userName);
                  setProfileModal(null);
                }}
            />
        )}
        {profileModal === "title" && (
            <TitleSelectModal onClose={() => setProfileModal(null)} onChanged={setRpgStats} />
        )}
      </AppLayout>
  );
}

// ================= 북마크 카드 (관광정보) =================
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
      <div className="group relative tp-card overflow-hidden tp-lift">
        <button
            type="button"
            onClick={(e) => {
              e.preventDefault();
              onRemove();
            }}
            title="북마크 해제"
            className="absolute right-3 top-3 z-10 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-xs text-slate-500 shadow-lg backdrop-blur transition hover:bg-rose-500 hover:text-white"
        >
          ✕
        </button>

        <Link
            to={`/tour/${b.categoryKey}/${b.contentId}${
                b.contentTypeId ? `?contentTypeId=${b.contentTypeId}` : ""
            }`}
            className="block"
        >
          <div className="relative m-2 h-36 overflow-hidden rounded-2xl bg-slate-100">
            <img
                src={b.imageUrl ?? FALLBACK_IMAGE}
                alt={b.title}
                className="h-full w-full object-cover transition-transform duration-700 group-hover:scale-110"
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
          <div className="px-4 pb-4 pt-2">
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

// ================= 북마크 카드 (금융상품) =================
// 저장 시점의 요약 스냅샷만 갖고 있어서(api/financeBookmark.ts 참고) 관광정보 카드보다 단순합니다.
// 상세 옵션표를 다시 보려면 /finance 페이지로 돌아가서 확인합니다.
function FinanceBookmarkCard({
                               bookmark: b,
                               onRemove,
                             }: {
  bookmark: FinancialProductBookmark;
  onRemove: () => void;
}) {
  return (
      <div className="group relative tp-card overflow-hidden tp-lift">
        <button
            type="button"
            onClick={(e) => {
              e.preventDefault();
              onRemove();
            }}
            title="북마크 해제"
            className="absolute right-3 top-3 z-10 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 text-xs text-slate-500 shadow-lg backdrop-blur transition hover:bg-rose-500 hover:text-white"
        >
          ✕
        </button>

        <Link to="/finance" className="block p-5">
          <div className="flex items-center gap-2 mb-2">
          <span className="px-2 py-0.5 bg-sky-50 text-sky-600 text-[11px] font-bold rounded-full">
            {b.korCoNm}
          </span>
            <span className="px-2 py-0.5 bg-slate-100 text-slate-500 text-[10px] font-semibold rounded-full">
            {b.productType === "saving" ? "적금" : "정기예금"}
          </span>
          </div>
          <h3 className="font-bold text-slate-800 text-sm mb-2 line-clamp-2">
            {b.finPrdtNm}
          </h3>
          <p className="text-xs text-slate-400 mb-1">최고우대금리</p>
          <p className="font-display text-3xl font-bold tracking-tight tp-gradient-text">{formatRate(b.maxRate)}</p>
          {b.joinWay && (
              <p className="text-[11px] text-slate-400 mt-2 line-clamp-1">가입방법 {b.joinWay}</p>
          )}
        </Link>
      </div>
  );
}

// 스켈레톤 로더 (북마크)
function BookmarkGridSkeleton() {
  return (
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
        {Array.from({ length: 3 }).map((_, i) => (
            <div
                key={i}
                className="h-56 rounded-3xl tp-card animate-pulse"
            />
        ))}
      </div>
  );
}

// 빈 상태 컴포넌트
function EmptyState({ label }: { label: string }) {
  return (
      <div className="tp-empty col-span-full text-center py-16 text-slate-400 text-sm">
        {label}
      </div>
  );
}