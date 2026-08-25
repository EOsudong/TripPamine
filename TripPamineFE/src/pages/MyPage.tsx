// 마이페이지 — 로그인한 사용자만 접근 가능 (router/Router.tsx의 ProtectedRoute가 보호함).
// 북마크(저장)한 관광정보를 "저장된 축제 및 행사 / 저장된 관광 여행지 / 저장된 관광 산업" 3개 탭으로
// 모아 보여주고, 가계부도 여기서 관리
// [닉네임] "여행자님" 고정 문구 대신 로그인한 사용자의 실제 닉네임(userName)을 /users/auth/me로 받아와 표시합니다.
// [계좌 기능 추가] 사용자가 계좌정보를 직접 입력해 등록/조회하는 ManualAccountSection을 추가함.
// [가계부 이동] 메인 페이지(Hero.tsx)에 있던 여행 가계부(AccountBook)를 이 페이지의 "가계부" 탭으로 옮김.
// [퀘스트 실서버 연동] 더미 배열 대신 GET /quests + GET /quests/my-logs를 실제로 호출해서 퀘스트
// 목록/진행 상태를 그린다. 포인트/등급도 백엔드가 내려준 값을 그대로 신뢰하고, 프론트에서 임의로
// 계산하지 않는다 (서버가 유일한 진실 소스).
import {useEffect, useMemo, useState} from "react";
import {Link, useSearchParams} from "react-router-dom";
import Header from "../components/Header";
import Sidebar from "../components/Sidebar";
import Footer from "../components/Footer";
import ManualAccountSection from "../components/ManualAccountSection";
import AccountBook from "../components/AccountBook";
import {getMyInfoApi} from "../api/auth";
import {getBookmarksApi, removeBookmarkApi} from "../api/bookmark";
import type {TourBookmark, TourMainCategoryKey} from "../types";
import {QuestMapView} from "../components/QuestMapView";
import {RealtimeQuestVerifier} from "../components/RealtimeQuestVerifier";
import {
    extractQuestErrorMessage,
    questApi,
    type QuestResponse,
    type UserQuestLogResponse,
} from "../api/quest";

const FALLBACK_IMAGE =
    "https://images.unsplash.com/photo-1500835556837-99ac94a94552?w=900&h=600&fit=crop&auto=format";

// 북마크 탭 3개 — data/tourCategories.ts의 대분류(festivals/destinations/industry)와 동일한 키를 씀
const BOOKMARK_TABS: {
    key: TourMainCategoryKey;
    label: string;
    icon: string;
}[] = [
    {key: "festivals", label: "저장된 축제 및 행사", icon: "🎉"},
    {key: "destinations", label: "저장된 관광 여행지", icon: "🗺️"},
    {key: "industry", label: "저장된 관광 산업", icon: "🏨"},
];

type Tab = TourMainCategoryKey | "accountBook" | "quest";

function formatEventDate(yyyymmdd: string | null): string {
    if (!yyyymmdd || yyyymmdd.length !== 8) return "";
    return `${yyyymmdd.slice(0, 4)}.${yyyymmdd.slice(4, 6)}.${yyyymmdd.slice(6, 8)}`;
}

export default function MyPage() {
    const [searchParams] = useSearchParams();
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [tab, setTab] = useState<Tab>("festivals");

    // [Mock 은행 연동 추가] ManualAccountSection(계좌 목록/잔액)과 AccountBook(가계부) 사이의
    // 새로고침 신호를 여기서 중계한다 - 둘 중 하나가 계좌 잔액에 영향을 줄 수 있는 동작을 하면
    // (계좌 연동/삭제, 계좌 지정 가계부 입력/수정/삭제) 반대쪽 값을 증가시켜 재조회를 유도한다.
    const [accountsVersion, setAccountsVersion] = useState(0);
    const [ledgerVersion, setLedgerVersion] = useState(0);

    // 사용자 프로필 정보 관리 상태 - 전부 /users/auth/me 응답을 그대로 신뢰
    const [userName, setUserName] = useState<string | null>(null);
    const [userGrade, setUserGrade] = useState<string>("Bronze");
    const [totalPoints, setTotalPoints] = useState<number>(0);

    // 북마크 상태
    const [bookmarks, setBookmarks] = useState<TourBookmark[]>([]);
    const [bookmarksLoading, setBookmarksLoading] = useState(true);
    const [bookmarksError, setBookmarksError] = useState<string | null>(null);

    // 퀘스트 상태 - 목록(마스터)과 내 진행 이력을 각각 조회해서 questId 기준으로 합쳐 보여준다
    const [quests, setQuests] = useState<QuestResponse[]>([]);
    const [myLogs, setMyLogs] = useState<UserQuestLogResponse[]>([]);
    const [questsLoading, setQuestsLoading] = useState(true);
    const [questsError, setQuestsError] = useState<string | null>(null);
    const [activeQuest, setActiveQuest] = useState<QuestResponse | null>(null);
    const [questsRetryToken, setQuestsRetryToken] = useState(0); // 재시도용 트리거

    // 탭 클릭 핸들러
    const handleTabClick = (tabKey: Tab) => {
        setTab(tabKey);
    };

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

    // 로그인한 사용자 정보 조회 (닉네임/등급/포인트 - 헤더 인사말 + 퀘스트 스탯 카드에 사용)
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
    }, []);

    // 탭이 3개 북마크 카테고리 중 하나로 바뀔 때마다 해당 카테고리 북마크 목록을 새로 받아옴
    useEffect(() => {
        if (tab === "accountBook" || tab === "quest") return;

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

    // 퀘스트 탭에 처음 진입할 때 목록 + 내 이력을 함께 조회
    useEffect(() => {
        if (tab !== "quest") return;

        let cancelled = false;
        setQuestsLoading(true);
        setQuestsError(null);

        Promise.all([questApi.getQuests(), questApi.getMyLogs()])
            .then(([questList, logList]) => {
                if (cancelled) return;
                setQuests(questList);
                setMyLogs(logList);
            })
            .catch((error) => {
                console.error("퀘스트 목록 조회 실패:", error);
                if (!cancelled)
                    setQuestsError(
                        extractQuestErrorMessage(
                            error,
                            "퀘스트 목록을 불러오지 못했어요. 잠시 후 다시 시도해주세요.",
                        ),
                    );
            })
            .finally(() => {
                if (!cancelled) setQuestsLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [tab, questsRetryToken]);

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

    // questId → 내 로그 매핑 (없으면 아직 시작 안 한 퀘스트)
    const logByQuestId = useMemo(() => {
        const map = new Map<number, UserQuestLogResponse>();
        myLogs.forEach((log) => map.set(log.questId, log));
        return map;
    }, [myLogs]);

    // RealtimeQuestVerifier가 최신 로그(시작 직후, 클리어 성공/실패 직후)를 올려줄 때마다
    // 목록/이력/포인트 상태를 서버가 내려준 값 그대로 반영한다.
    const handleLogUpdate = (log: UserQuestLogResponse) => {
        setMyLogs((prev) => {
            const idx = prev.findIndex((l) => l.logId === log.logId);
            if (idx === -1) return [log, ...prev];
            const next = [...prev];
            next[idx] = log;
            return next;
        });

        if (log.status === "SUCCESS") {
            // 서버가 실제로 지급한 rewardPoint만 반영 (프론트 임의 계산 없음)
            setTotalPoints((prev) => prev + log.rewardPoint);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50">
            <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)}/>
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
                    <div
                        className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 flex items-center justify-between gap-4 mb-8 flex-wrap">
                        <div className="flex items-center gap-4">
                            <div
                                className="w-16 h-16 rounded-full bg-gradient-to-br from-sky-500 to-sky-400 flex items-center justify-center shrink-0 shadow-md">
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
                            onClick={() => handleTabClick("quest")}
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
                                {questsLoading ? (
                                    <QuestGridSkeleton/>
                                ) : questsError ? (
                                    <div
                                        className="text-center py-16 bg-white rounded-2xl border border-dashed border-slate-200 space-y-3">
                                        <p className="text-slate-400 text-sm">{questsError}</p>
                                        <button
                                            onClick={() => setQuestsRetryToken((v) => v + 1)}
                                            className="px-4 py-2 rounded-xl bg-sky-500 hover:bg-sky-600 text-white text-xs font-bold transition"
                                        >
                                            다시 시도
                                        </button>
                                    </div>
                                ) : activeQuest ? (
                                    /* 지도 검증 활성화 뷰 */
                                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                                        <div
                                            className="lg:col-span-2 rounded-3xl overflow-hidden border border-slate-100 shadow-sm h-[450px] bg-white">
                                            <QuestMapView quest={activeQuest}/>
                                        </div>
                                        <div
                                            className="flex flex-col justify-between rounded-3xl bg-white border border-slate-100 p-6 shadow-sm">
                                            <div>
                        <span
                            className="px-3 py-1 rounded-full text-xs font-bold bg-sky-50 text-sky-600 border border-sky-100">
                          진행 중인 미션
                        </span>
                                                <h3 className="text-xl font-bold text-slate-800 mt-4">
                                                    {activeQuest.questName}
                                                </h3>

                                                <div
                                                    className="mt-6 space-y-1.5 text-xs text-slate-400 border-t border-slate-100 pt-4">
                                                    <p>
                                                        📍 목표 좌표: {activeQuest.targetLat.toFixed(5)},{" "}
                                                        {activeQuest.targetLng.toFixed(5)}
                                                    </p>
                                                    <p>
                                                        📏 완료 인증 범위: 목표 지점 반경{" "}
                                                        {activeQuest.clearRadius}m 이내
                                                    </p>
                                                </div>
                                            </div>

                                            <div className="mt-6 space-y-2">
                                                <RealtimeQuestVerifier
                                                    quest={activeQuest}
                                                    onLogUpdate={handleLogUpdate}
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
                                ) : quests.length === 0 ? (
                                    <EmptyState label="현재 도전 가능한 퀘스트가 없어요"/>
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
                                            {quests.map((q) => {
                                                const log = logByQuestId.get(q.questId);
                                                const status = log?.status ?? "NOT_STARTED";
                                                const isSuccess = status === "SUCCESS";
                                                const isFailed = status === "FAILED";
                                                const isProgress = status === "PROGRESS";

                                                return (
                                                    <div
                                                        key={q.questId}
                                                        className={`rounded-2xl border p-5 transition flex flex-col justify-between bg-white shadow-sm ${
                                                            isSuccess
                                                                ? "border-emerald-100 bg-emerald-50/20"
                                                                : "border-slate-100 hover:border-sky-200 hover:shadow-md"
                                                        }`}
                                                    >
                                                        <div>
                                                            <div className="flex items-start justify-between gap-2">
                                                                <h4 className="text-base font-bold text-slate-800 flex items-center gap-1.5">
                                                                    {isSuccess ? "✅" : isFailed ? "🔁" : "📌"}{" "}
                                                                    {q.questName}
                                                                </h4>
                                                                <span
                                                                    className={`text-xs px-2.5 py-0.5 rounded-full font-bold shrink-0 ${
                                                                        isSuccess
                                                                            ? "bg-emerald-100 text-emerald-700"
                                                                            : isFailed
                                                                                ? "bg-rose-50 text-rose-500"
                                                                                : isProgress
                                                                                    ? "bg-amber-50 text-amber-600"
                                                                                    : "bg-sky-50 text-sky-600"
                                                                    }`}
                                                                >
                                  {isSuccess
                                      ? "완료됨"
                                      : isFailed
                                          ? "재도전 가능"
                                          : isProgress
                                              ? "진행중"
                                              : `+${q.rewardPoint} P`}
                                </span>
                                                            </div>
                                                            <p className="text-xs text-slate-500 mt-2 leading-relaxed">
                                                                목표 지점 반경 {q.clearRadius}m 이내에 도달하면
                                                                GPS 검증으로 미션을 완료할 수 있어요.
                                                            </p>
                                                        </div>

                                                        <div className="mt-5">
                                                            {isSuccess ? (
                                                                <div
                                                                    className="w-full text-center py-2.5 rounded-xl bg-emerald-50 border border-emerald-100 text-emerald-600 text-xs font-bold">
                                                                    🎉 {q.rewardPoint.toLocaleString()} P 적립 완료
                                                                </div>
                                                            ) : (
                                                                <button
                                                                    onClick={() => setActiveQuest(q)}
                                                                    className="w-full rounded-xl bg-sky-500 hover:bg-sky-600 text-white py-2.5 text-xs font-bold shadow-sm transition"
                                                                >
                                                                    {isFailed
                                                                        ? "다시 도전하기"
                                                                        : isProgress
                                                                            ? "이어서 인증하기"
                                                                            : "지도 확인 및 실시간 GPS 인증"}
                                                                </button>
                                                            )}
                                                        </div>
                                                    </div>
                                                );
                                            })}
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
                                    <ManualAccountSection
                                        refreshSignal={ledgerVersion}
                                        onAccountsChanged={() => setAccountsVersion((v) => v + 1)}
                                    />
                                </div>

                                <div className="bg-white rounded-3xl shadow-sm border border-slate-100 p-6 md:p-8">
                                    <h3 className="text-base font-bold text-slate-800 mb-6">
                                        🧾 가계부 내역
                                    </h3>
                                    <AccountBook
                                        username={userName || "여행자"}
                                        accountsRefreshSignal={accountsVersion}
                                        onLedgerChanged={() => setLedgerVersion((v) => v + 1)}
                                    />
                                </div>
                            </div>
                        )}

                        {/* 3. 저장된 북마크 탭들 */}
                        {tab !== "accountBook" && tab !== "quest" && (
                            <div>
                                {bookmarksLoading ? (
                                    <BookmarkGridSkeleton/>
                                ) : bookmarksError ? (
                                    <EmptyState label={bookmarksError}/>
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

                <Footer/>
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
            ? {label: "진행중", bg: "bg-emerald-500"}
            : b.status === "upcoming"
                ? {label: "예정", bg: "bg-sky-500"}
                : null;

    return (
        <div
            className="group relative bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-lg border border-slate-100 transition-all duration-300">
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

// 스켈레톤 로더 (북마크)
function BookmarkGridSkeleton() {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {Array.from({length: 3}).map((_, i) => (
                <div
                    key={i}
                    className="h-56 rounded-2xl bg-white border border-slate-100 animate-pulse"
                />
            ))}
        </div>
    );
}

// 스켈레톤 로더 (퀘스트)
function QuestGridSkeleton() {
    return (
        <div className="grid gap-4 md:grid-cols-2">
            {Array.from({length: 2}).map((_, i) => (
                <div
                    key={i}
                    className="h-40 rounded-2xl bg-white border border-slate-100 animate-pulse"
                />
            ))}
        </div>
    );
}

// 빈 상태 컴포넌트
function EmptyState({label}: { label: string }) {
    return (
        <div
            className="col-span-full text-center py-16 text-slate-400 text-sm bg-white rounded-2xl border border-dashed border-slate-200">
            {label}
        </div>
    );
}

